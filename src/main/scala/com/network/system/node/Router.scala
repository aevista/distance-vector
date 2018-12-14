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

  final private[system] def shutdown(delay: Duration): Unit = scheduleOnce(delay){
    terminate()
    table.clear()
  }

  final private[system] def run(delay: Duration = Duration.Zero): Unit = scheduleOnce(delay){
    init()
    table.update(node, Route(node, 0))

    schedulePeriod(delay, FiniteDuration(1, TimeUnit.SECONDS)){ for {
      (dest, Route(_, weight)) <- table
    } yield advertise(DvPacket(dest, weight))}
  }

  /**
    * Receive.
    * State like receiver that handles incoming packets.
    *
    * @param packet DvPacket
    * @param interface Interface
    */

  final override protected def receive(packet: DvPacket)(interface: Interface): Unit = table(packet.dest) match {
    case Route(_, Connection.CLOSED) => packet.weight match {
      case Connection.CLOSED if interfaces.get(packet.dest).contains(interface) =>
        advertise(DvPacket(packet.dest, Connection.CLOSED))
      case Connection.CLOSED =>
      case 0 if interfaces.get(packet.dest).contains(interface) => accept(interface)
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

  /**
    * Accept.
    * Adds new Node route to table.
    * Sends new Node routing info.
    * Advertise new connection to other nodes.
    *
    * @param interface Interface
    */

  final private def accept(interface: Interface): Unit = {
    for {
      (dest, Route(_, weight)) <- table
    } route(DvPacket(dest, weight))(interface)

    table.update(interface.node, Route(interface.node, interface.link.weight))
    advertise(DvPacket(interface.node, interface.link.weight))
  }

  /**
    * Release.
    * Removes all entries that contain dropped Node.
    * Advertises lost connections and advertises itself.
    *
    * @param interface Interface
    */

  final private def release(interface: Interface): Unit = {
    for {
      (dest, Route(nh, _)) <- table
      if dest == interface.node || nh == interface.node
    } yield table.remove(dest)

    advertise(DvPacket(interface.node, Connection.CLOSED))
    // advertises itself due to the possibility of this now
    // being the shortest path to get to this node
    advertise(DvPacket(node, 0))
  }

  /**
    * Advertise.
    * Routes packets to interfaces via split horizon.
    * Don't advertise a route to a node on how to get to itself.
    *
    * @param packet DvPacket
    */

  final private def advertise(packet: DvPacket): Unit = for {
    interface <- splitHorizon(packet.dest)
    if packet.dest != interface.node
  } yield route(packet)(interface)

  /**
    * SplitHorizon.
    * Don't advertise a route to get to Node A from Node B
    * to Node B.
    *
    * @param dest Node
    * @return interfaces
    */

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
