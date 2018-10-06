package com.network.system.node

import java.util.concurrent.TimeUnit

import com.network.connection.{Connection, EndPoint}
import com.network.system.Network
import com.network.packet.DvPacket
import com.network.system.routing.Routing
import com.network.system.state.{Idle, Running, State}
import com.network.util.Route

import scala.collection.{mutable => m}
import scala.concurrent.duration.{Duration, FiniteDuration}

case class Router private[system](node: Node, network: Network) extends Routing(network) {

  private val table = m.Map.empty[Node, Route]
  private var state: State = Idle

  final def run(delay: Duration = Duration.Zero): Unit = scheduleOnce(delay)(state match {
    case Running =>
    case Idle =>
      state = Running
      init()
      for {
        (dest, Route(_, weight)) <- table
      } yield advertise(DvPacket(dest, weight))
  })

  final def shutdown(time: Duration): Unit = scheduleOnce(time)(state match {
    case Idle =>
    case Running => for {
      (dest, Route(endPoint, _)) <- table
      _ = table.update(dest, Route(endPoint, Connection.CLOSED))
      _ = endPoint.close()
    } yield advertise(DvPacket(node, Connection.CLOSED))
      state = Idle
  })

  final private def init(): Unit = {
    for {
      (node, endPoint) <- endPoints
      _ = endPoint.open()
    } yield table.update(node, Route(endPoint, endPoint.link.weight))

    table.update(node, Route(EndPoint(node), 0))

    schedulePeriodic(FiniteDuration(1, TimeUnit.SECONDS))(for {
      (dest, Route(_, weight)) <- table
    } yield advertise(DvPacket(dest, weight)))
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

  final private def splitHorizon(dest: Node): Set[EndPoint] = for {
    endPoint <- table.values.map(_.nextHop).toSet
    if table.get(dest).exists(_.nextHop != endPoint)
  } yield endPoint

  final override def toString: String = {
    table.toList.sortBy(_._1.id)( Ordering.String)
      .foldLeft(s"Router ${node.id}") { case (str, (dest, Route(nextHop, weight))) =>
        str + s"\n${dest.id} | ${nextHop.node.id} | $weight"
    }.concat("\n")
  }

}
