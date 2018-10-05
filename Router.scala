package com.network.node

import java.util.concurrent.TimeUnit

import com.network.connection.{Connection, EndPoint}
import com.network.manager.{Network, Routing}
import com.network.packet.DvPacket
import com.network.util.Route

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

case class Router(node: Node, network: Network) extends Routing(network) {

  private val table = mutable.Map.empty[String, Route]

  final def init(endPoint: EndPoint): Unit = {
    println(s"init $endPoint")
    table.update(endPoint.node.id, Route(endPoint, endPoint.link.weight))

    schedule(FiniteDuration(1, TimeUnit.SECONDS))( for {
      (dest, Route(_, weight)) <- table.toList
    } yield advertise(DvPacket(dest, weight)))
  }

  final def run(): Unit = for {
    (dest, Route(_, weight)) <- table
  } yield advertise(DvPacket(dest, weight))

  final def shutDown(time: FiniteDuration): Unit = {
    scheduleOnce(time)(for {
      (_, Route(endPoint, _)) <- table.toSet
    } yield endPoint.close(time))
  }

  final override protected def receive(packet: DvPacket): Unit = packet match {

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
    if table.get(packet.dest).fold(true)(_.nextHop != endPoint)
     _ = println(s"advertising $packet to ${endPoint.node.id} from ${node.id}")
  } yield route(packet)(endPoint)


  final override def toString: String =
    table.foldRight(s"Router ${node.id}"){ case ((dest, Route(nextHop, weight)), str) =>
        str + s"\n$dest | ${nextHop.node.id} | $weight"
    }

}
