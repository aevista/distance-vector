package com.network.system.node

import java.util.concurrent.TimeUnit

import com.network.connection.{Connection, EndPoint}
import com.network.manager.Network
import com.network.packet.DvPacket
import com.network.system.routing.Routing
import com.network.system.state.{Idle, Running, State}
import com.network.util.Route

import scala.collection.{mutable => m}
import scala.concurrent.duration.{Duration, FiniteDuration}

case class Router private[system](node: Node, network: Network) extends Routing(network) {

  private val table = m.Map.empty[String, Route]
  private val self: EndPoint = EndPoint(node)
  private var state: State = Idle

  final def init(endPoint: EndPoint): Unit = table.get(endPoint.node.id) match {

    case Some(_) =>
      println(s"${node.id} already connected to ${endPoint.node.id}")

    case None =>
      println(s"${node.id} init $endPoint")
      table.update(endPoint.node.id, Route(endPoint, endPoint.link.weight))

      schedule(FiniteDuration(1, TimeUnit.SECONDS))(for {
        (dest, Route(_, weight)) <- table
      } yield advertise(DvPacket(dest, weight)))
  }

  final def scheduleShutdown(time: Duration): Unit = {
    scheduleOnce(time)({ for {
      (dest, Route(endPoint, _)) <- table
      if endPoint != self
    } yield { table.update(dest, Route(endPoint, Connection.CLOSED));  endPoint.close(time) }
      state = Idle
    })
  }

  final def run(): Unit = state match {
    case Running =>
    case Idle => for {
      (dest, Route(_, weight)) <- table
    } yield advertise(DvPacket(dest, weight))
      table.update(node.id, Route(self, 0))
      state = Running
  }

  final override protected def receive(packet: DvPacket)(endPoint: EndPoint): Unit = packet match {

    case DvPacket(dest, weight) => table.get(dest) match {
      case Some(Route(nh, w)) if nh == endPoint && w != weight =>
        val newWeight = if (weight == Connection.CLOSED) weight else weight + endPoint.link.weight
        println(s"${node.id} removing connection to $dest")
        table.update(dest, Route(endPoint, newWeight))
        advertise(DvPacket(dest, newWeight))

      case Some(Route(_, w)) if weight + endPoint.link.weight < w =>
        println(s"${node.id} updating dest $dest with (nh: ${endPoint.node.id}, weight $weight)")
        table.update(dest, Route(endPoint, weight + endPoint.link.weight))
        advertise(DvPacket(dest, weight + endPoint.link.weight))

      case None  =>
        val newWeight = if (weight == Connection.CLOSED) weight else weight + endPoint.link.weight
        println(s"${node.id} adding dest $dest with (nh: ${endPoint.node.id}, weight $newWeight)")
        table.update(dest, Route(endPoint, newWeight))
        advertise(DvPacket(dest, newWeight))

      case _ => println(s"${node.id} dropping dest $dest with (nh: ${endPoint.node.id}, weight $weight)")
    }
  }

  final private def advertise(packet: DvPacket): Unit = for {
    endPoint <- splitHorizon(packet.dest)
     _ = println(s"advertising $packet to ${endPoint.node.id} from ${node.id}")
  } yield route(packet)(endPoint)

  final private def splitHorizon(dest: String): Set[EndPoint] = for {
    endPoint <- table.values.map(_.nextHop).toSet
    if table.get(dest).exists(_.nextHop != endPoint)
    if endPoint != self
  } yield endPoint

  final override def toString: String = {
    table.toList.sortBy(_._1)( Ordering.String)
      .foldLeft(s"Router ${node.id}") { case (str, (dest, Route(nextHop, weight))) =>
        str + s"\n$dest | ${nextHop.node.id} | $weight"
    }.concat("\n")
  }

}
