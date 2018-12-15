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
    * @param dvp DvPacket
    * @param i Interface
    */

  final override protected def receive(dvp: DvPacket)(i: Interface): Unit = table(dvp.dest) match {
    case Route(_, Connection.CLOSED) => closedRoute()(dvp)(i)
    case r: Route => openedRoute(r)(dvp)(i)
  }

  final private def closedRoute()(dvp: DvPacket)(i: Interface): Unit = dvp.weight match {
    case Connection.CLOSED if interfaces.get(dvp.dest).contains(i) => advertise(dvp)
    case Connection.CLOSED =>
    case 0 if interfaces.get(dvp.dest).contains(i) => accept(i)
    case _ if interfaces.contains(dvp.dest) =>
    case weight =>
      table.update(dvp.dest, Route(i.node, weight + i.link.weight))
      advertise(DvPacket(dvp.dest, weight + i.link.weight))
  }

  final private def openedRoute(r: Route)(dvp: DvPacket)(i: Interface): Unit = dvp.weight match {
    case Connection.CLOSED if interfaces.get(dvp.dest).contains(i) => release(i)
    case Connection.CLOSED if interfaces.contains(dvp.dest) =>
    case Connection.CLOSED =>
      table.remove(dvp.dest)
      advertise(dvp)
    case weight if r.nextHop == i.node && r.weight != weight + i.link.weight
      || weight + i.link.weight < r.weight =>
      table.update(dvp.dest, Route(i.node, weight + i.link.weight))
      advertise(DvPacket(dvp.dest, weight + i.link.weight))
    case _ =>
  }

  /**
    * Accept.
    * Adds new Node route to table.
    * Sends new Node routing info.
    * Advertise new connection to other nodes.
    *
    * @param i Interface
    */

  final private def accept(i: Interface): Unit = {
    for {
      (dest, Route(_, weight)) <- table
    } route(DvPacket(dest, weight))(i)

    table.update(i.node, Route(i.node, i.link.weight))
    advertise(DvPacket(i.node, i.link.weight))
  }

  /**
    * Release.
    * Removes all entries that contain dropped Node.
    * Advertises lost connections and advertises itself.
    *
    * @param i Interface
    */

  final private def release(i: Interface): Unit = {
    for {
      (dest, Route(nh, _)) <- table
      if dest == i.node || nh == i.node
    } yield table.remove(dest)

    advertise(DvPacket(i.node, Connection.CLOSED))
    // advertises itself due to the possibility of this now
    // being the shortest path to get to this node
    advertise(DvPacket(node, 0))
  }

  /**
    * Advertise.
    * Routes packets to interfaces via split horizon.
    * Don't advertise a route to a node on how to get to itself.
    *
    * @param dvp DvPacket
    */

  final private def advertise(dvp: DvPacket): Unit = for {
    interface <- splitHorizon(dvp.dest)
    if dvp.dest != interface.node
  } yield route(dvp)(interface)

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
