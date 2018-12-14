package com.network.system.routing

import java.util.concurrent.TimeUnit

import com.network.connection.{Connection, Interface}
import com.network.control.Control
import com.network.event.ControlEvent
import com.network.system.Network
import com.network.packet.{DvPacket, NetworkPacket}
import com.network.system.node.Node
import com.network.system.state.{Idle, Running, State}
import com.network.util.{Ack, Periodic, Reason, Triggered}

import scala.concurrent.duration.{Duration, FiniteDuration}

abstract private[system] class Routing(node: Node, network: Network) {

  private var state: State = Idle
  private var currentTime: Duration = FiniteDuration(0, TimeUnit.SECONDS)
  private var _interfaces = Map.empty[Node, Interface]

  final def connect(interface: Interface): Unit =
    _interfaces += interface.node -> interface

  final protected def interfaces: Map[Node, Interface] = _interfaces

  final protected def init(): Unit = {
    println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node initializing")
    state = Running
    for {
      interface <- interfaces.values
      _ = interface.open()
    } yield route(DvPacket(node, 0))(interface)
  }

  final protected def terminate(): Unit = {
    println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node terminating")
    for {
      interface <- interfaces.values
      _ = interface.close()
    } yield route(DvPacket(node, Connection.CLOSED))(interface)
    state = Idle
  }

  final def incoming(packet: NetworkPacket)(interface: Interface): Unit = state match {
    case Running =>
      println(s"TIME ${"%10d".format(packet.elapsedTime.toMicros)}: $node receiving $packet from ${interface.node}")
      currentTime = packet.elapsedTime
      receive(packet.dvPacket)(interface)
    case Idle =>
  }

  final protected def schedulePeriod(delay: Duration, period: Duration)(event: => Unit): Unit = {

    def update(elapsedTime: Duration): Unit = {
      val control = Control[Ack]()
        .filter(_ => state == Running)
        .andThen(_ => currentTime = elapsedTime)
        .andThen(_ => event)
        .andThen(ack => update(ack.time + period))

      publish(control, elapsedTime, Periodic)
    }

    update(delay + period)
  }

  final protected def scheduleOnce(time: Duration)(event: => Unit): Unit = {
    val control = Control[Ack]()
        .andThen(_ => currentTime = time)
        .andThen(_ => event)

    publish(control, time, Triggered)
  }

  final protected def route(packet: DvPacket)(interface: Interface): Unit = {
    val _state = state
    val control = Control[Ack]()
      .filter(_ => _state == Running)
      .andThen(ack => println(s"TIME ${"%10d".format(ack.time.toMicros)}: $node routing $packet to ${interface.node}"))
      .andThen(ack => interface.send(NetworkPacket(packet, ack.time)))

    println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node scheduling route $packet to ${interface.node}")

    publish(control, currentTime + interface.link.delay, Triggered)
  }

  final private def publish(control: Control[Ack, Ack], time: Duration, reason: Reason): Unit = {
    network.publish(ControlEvent(control, time, reason))
  }

  protected def receive(packet: DvPacket)(endPoint: Interface): Unit

}
