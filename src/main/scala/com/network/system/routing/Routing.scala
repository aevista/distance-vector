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

abstract class Routing(node: Node, network: Network) {

  private var state: State = Idle
  private var currentTime: Duration = FiniteDuration(0, TimeUnit.SECONDS)
  private var _interfaces = Map.empty[Node, Interface]

  final def connect(interface: Interface): Unit =
    _interfaces += interface.node -> interface

  final protected def interfaces: Map[Node, Interface] = _interfaces

  final protected def init(): Unit = {
    for {
      interface <- interfaces.values
      _ = interface.open()
    } yield route(DvPacket(node, 0))(interface)
    state = Running
  }

  final protected def terminate(): Unit = {
    for {
      interface <- interfaces.values
      _ = interface.close()
    } yield route(DvPacket(node, Connection.CLOSED))(interface)
    state = Idle
  }

  final def incoming(packet: NetworkPacket)(endPoint: Interface): Unit = {
    currentTime = packet.elapsedTime
    receive(packet.dvPacket)(endPoint)
  }

  final protected def schedulePeriod(delay: Duration, period: Duration)(event: => Unit): Unit = {

    def update(elapsedTime: Duration): Unit = {
      val control = Control[Ack]()
        .filter(_ => state == Running)
        .andThen(_ => event)
        .andThen(ack => update(ack.time + period))

      publish(control, elapsedTime, Periodic)
    }

    update(delay + period)
  }

  final protected def scheduleOnce(time: Duration)(event: => Unit): Unit = {
    publish(Control().andThen(_ => event), time, Triggered)
  }

  final protected def route(packet: DvPacket)(interface: Interface): Unit = {
    val control = Control[Ack]()
      .andThen(ack => interface.send(NetworkPacket(packet, ack.time)))

    publish(control, currentTime + interface.link.delay, Triggered)
  }

  final private def publish(control: Control[Ack, Ack], time: Duration, reason: Reason): Unit = {
    network.publish(ControlEvent(control, time, reason))
  }

  protected def receive(packet: DvPacket)(endPoint: Interface): Unit

}
