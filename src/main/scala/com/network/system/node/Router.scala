package com.network.system.node

import java.util.concurrent.TimeUnit

import com.network.connection.{Connection, Interface}
import com.network.system.Network
import com.network.packet.DvPacket
import com.network.system.routing.Routing
import com.network.util.Route

import scala.collection.{mutable => m}
import scala.concurrent.duration.{Duration, FiniteDuration}

case class Router private[system](node: Node, network: Network) extends Routing(node, network) {

  private val table = m.Map.empty[Node, Route]
    .withDefault(Route(_, Connection.CLOSED))

  final def shutdown(delay: Duration): Unit = scheduleOnce(delay){
    terminate()
    table.clear()
  }

  final def run(delay: Duration = Duration.Zero): Unit = scheduleOnce(delay){
    init()
    table.update(node, Route(node, 0))

    schedulePeriod(delay, FiniteDuration(1, TimeUnit.SECONDS)){ for {
      (dest, Route(_, weight)) <- table
    } yield advertise(DvPacket(dest, weight))}
  }

  final override protected def receive(packet: DvPacket)(interface: Interface): Unit = table(packet.dest) match {
    case Route(_, Connection.CLOSED) => packet.weight match {
      case Connection.CLOSED => table.remove(packet.dest)
      case _ if interfaces.get(packet.dest).contains(interface) => accept(interface)
      case _ if interfaces.contains(packet.dest) =>
      case weight =>
        table.update(packet.dest, Route(interface.node, weight + interface.link.weight))
        advertise(DvPacket(packet.dest, weight + interface.link.weight))
    }
    case Route(nh, w) => packet.weight match {
      case Connection.CLOSED if interfaces.get(packet.dest).contains(interface) => release(interface)
      case Connection.CLOSED if interfaces.contains(packet.dest) =>
      case Connection.CLOSED =>
        table.remove(packet.dest)
        advertise(DvPacket(packet.dest, Connection.CLOSED))
      case weight if nh == interface.node && w != weight + interface.link.weight
        || weight + interface.link.weight < w =>
        table.update(packet.dest, Route(interface.node, weight + interface.link.weight))
        advertise(DvPacket(packet.dest, weight + interface.link.weight))
      case _ =>
    }
  }

  final private def accept(interface: Interface): Unit = {
    for {
      (dest, Route(_, weight)) <- table
      if splitHorizon(dest).contains(interface)
    } route(DvPacket(dest, weight))(interface)

    table.update(interface.node, Route(interface.node, interface.link.weight))
    advertise(DvPacket(interface.node, interface.link.weight))
  }

  final private def release(interface: Interface): Unit = for {
    (dest, Route(nh, _)) <- table
    if dest == interface.node || nh == interface.node
    _ = table.remove(dest)
  } yield advertise(DvPacket(dest, Connection.CLOSED))

  final private def advertise(packet: DvPacket): Unit = for {
    interface <- splitHorizon(packet.dest)
     _ = println(s"advertising $packet to ${interface.node.id} from ${node.id}")
  } yield route(packet)(interface)

  final private def splitHorizon(dest: Node): Set[Interface] = for {
    interface <- interfaces.values.toSet
    if table.get(dest).fold(true)(_.nextHop != interface.node)
  } yield interface

  final override def toString: String = {
    table.toList.sortBy(_._1.id)( Ordering.String)
      .foldLeft(s"Router ${node.id}") { case (str, (dest, Route(nh, weight))) =>
        str + s"\n${dest.id} | ${nh.id} | $weight"
    }.concat("\n")
  }

}
