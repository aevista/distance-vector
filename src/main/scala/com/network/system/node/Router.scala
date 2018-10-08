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
  private val self: EndPoint = EndPoint(node)

  final def run(delay: Duration = Duration.Zero): Unit = scheduleOnce(delay)(state match {
    case Running =>
    case Idle =>
      init(delay)
      state = Running
      for {
        (dest, Route(_, weight)) <- table
      } yield advertise(DvPacket(dest, weight))
  })

  final def shutdown(delay: Duration): Unit = scheduleOnce(delay)(state match {
    case Idle =>
    case Running => for {
      (dest, Route(endPoint, _)) <- table
      _ = table.update(dest, Route(endPoint, Connection.CLOSED))
      _ = endPoint.close()
    } yield advertise(DvPacket(node, Connection.CLOSED))
      state = Idle
  })

  final private def init(delay: Duration): Unit = {
    for {
      (node, endPoint) <- endPoints
      _ = endPoint.open()
    } yield table.update(node, Route(endPoint, endPoint.link.weight))

    table.update(node, Route(self, 0))

    println(toString)

    schedulePeriodic(delay + FiniteDuration(1, TimeUnit.SECONDS))( for {
      (dest, Route(_, weight)) <- table
    } yield advertise(DvPacket(dest, weight)))
  }

  final override protected def receive(packet: DvPacket)(endPoint: EndPoint): Unit = {

    val (dest, weight) = (packet.dest, packet.weight)
    val advWeight = if (weight == Connection.CLOSED) weight else weight + endPoint.link.weight

    table.get(dest) match {
      case Some(Route(_, w)) if advWeight != Connection.CLOSED && w == Connection.CLOSED =>
        println(s"${node.id} regained connection to $dest with $advWeight")
        table.update(dest, Route(endPoint, advWeight))
        advertise(DvPacket(dest, advWeight))

      case Some(Route(nh, w)) if nh != endPoint && advWeight == Connection.CLOSED && w != Connection.CLOSED =>
        println(s"${node.id} advertising alternative route to $dest with $advWeight")
        advertise(DvPacket(dest, w))

      case Some(Route(nh, w)) if nh == endPoint && w != advWeight =>
        println(s"${node.id} updating new cost of route to $dest from same hop ${nh.node.id} with $advWeight")
        table.update(dest, Route(endPoint, advWeight))
        advertise(DvPacket(dest, advWeight))

      case Some(Route(_, w)) if advWeight < w =>
        println(s"${node.id} updating dest $dest with new hop (nh: ${endPoint.node.id}, weight $weight)")
        table.update(dest, Route(endPoint, advWeight))
        advertise(DvPacket(dest, advWeight))

      case Some(_) =>
        println(s"${node.id} dropping dest $dest with (nh: ${endPoint.node.id}, weight $weight)")

      case None =>
        println(s"${node.id} adding dest $dest with (nh: ${endPoint.node.id}, weight $advWeight)")
        table.update(dest, Route(endPoint, advWeight))
        advertise(DvPacket(dest, advWeight))
    }
  }

  final private def advertise(packet: DvPacket): Unit = for {
    endPoint <- splitHorizon(packet.dest)
    if endPoint != self
     _ = println(s"advertising $packet to ${endPoint.node.id} from ${node.id} with ${endPoint.link}")
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
