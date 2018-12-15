package com.network.system.router

import java.util.concurrent.TimeUnit

import com.network.system.node.Node
import com.network.system.routing.connection.{Connection, Interface}
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

    schedulePeriodic(delay, FiniteDuration(1, TimeUnit.SECONDS)){ for {
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
    case Route(_, Connection.CLOSED) => closedRoute()(packet)(interface)
    case route => openedRoute(route)(packet)(interface)
  }

  final private def closedRoute()(p: DvPacket)(i: Interface): Unit = p.weight match {
    case Connection.CLOSED if interfaces.get(p.dest).contains(i) => advertise(p)
    case Connection.CLOSED =>
    case 0 if interfaces.get(p.dest).contains(i) => accept(i)
    case _ if interfaces.contains(p.dest) =>
    case weight =>
      table.update(p.dest, Route(i.node, weight + i.link.weight))
      advertise(DvPacket(p.dest, weight + i.link.weight))
  }

  final private def openedRoute(r: Route)(p: DvPacket)(i: Interface): Unit = p.weight match {
    case Connection.CLOSED if interfaces.get(p.dest).contains(i) => release(i)
    case Connection.CLOSED if interfaces.contains(p.dest) =>
    case Connection.CLOSED =>
      table.remove(p.dest)
      advertise(p)
    case weight if r.nextHop == i.node && r.weight != weight + i.link.weight
      || weight + i.link.weight < r.weight =>
      table.update(p.dest, Route(i.node, weight + i.link.weight))
      advertise(DvPacket(p.dest, weight + i.link.weight))
    case _ =>
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
