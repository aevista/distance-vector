package com.network.system

import java.util.concurrent.TimeUnit

import com.network.connection.{Connection, EndPoint}
import com.network.system.routing.Routing
import com.network.manager.Network
import com.network.node.Node
import com.network.packet.DvPacket
import com.network.util.Route

import scala.collection.{mutable => m}
import scala.concurrent.duration.FiniteDuration

case class Router private[system](node: Node, network: Network) extends Routing(network) {

  private val table = m.Map.empty[String, Route]
  private val self: EndPoint = EndPoint(node)

  final def init(endPoint: EndPoint): Unit = table.get(endPoint.node.id) match {

    case Some(_) =>
      println(s"${node.id} already connected to ${endPoint.node.id}")

    case None =>
      println(s"${node.id} init $endPoint")
      table.update(endPoint.node.id, Route(endPoint, endPoint.link.weight))

      schedule(FiniteDuration(1, TimeUnit.SECONDS))(for {
        (dest, Route(_, weight)) <- table.toList
      } yield advertise(DvPacket(dest, weight)))
  }

  final def shutDown(time: FiniteDuration): Unit = {
    scheduleOnce(time)(for {
      (_, Route(endPoint, _)) <- table.toSet
    } yield endPoint.close(time))
  }

  final def run(): Unit = {
    for {
      (dest, Route(_, weight)) <- table
    } yield advertise(DvPacket(dest, weight))

    table.update(node.id, Route(self, 0))
  }

  final override protected def receive(packet: DvPacket)(endPoint: EndPoint): Unit = packet match {

    case DvPacket(dest, weight) => table.get(dest) match {
      case Some(Route(nh, _)) if weight == Connection.CLOSED && nh == endPoint =>
        table.remove(dest)
        advertise(DvPacket(dest, Connection.CLOSED))

      case Some(Route(_, w)) if weight + endPoint.link.weight < w =>
        println(s"${node.id} updating dest $dest with (nh: ${endPoint.node.id}, weight $weight)")
        table.update(dest, Route(endPoint, weight + endPoint.link.weight))
        advertise(DvPacket(dest, weight + endPoint.link.weight))

      case None if weight != Connection.CLOSED =>
        println(s"${node.id} adding dest $dest with (nh: ${endPoint.node.id}, weight $weight)")
        table.update(dest, Route(endPoint, weight + endPoint.link.weight))
        advertise(DvPacket(dest, weight + endPoint.link.weight))

      case _ => println(s"${node.id} dropping dest $dest with (nh: ${endPoint.node.id}, weight $weight)")
    }
  }

  final private def advertise(packet: DvPacket): Unit = for {
    endPoint <- table.values.map(_.nextHop).toSet
    if table.get(packet.dest).exists(_.nextHop != endPoint)
    if endPoint != self
     _ = println(s"advertising $packet to ${endPoint.node.id} from ${node.id}")
  } yield route(packet)(endPoint)

  final override def toString: String = {
    table.toList.sortBy(_._1)( Ordering.String.reverse)
      .foldRight(s"Router ${node.id}") { case ((dest, Route(nextHop, weight)), str) =>
        str + s"\n$dest | ${nextHop.node.id} | $weight"
    }
  }

}
