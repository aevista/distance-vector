package com.network.system.routing

import java.util.concurrent.TimeUnit

import com.network.system.routing.connection.{Connection, Interface}
import com.network.control.Control
import com.network.event.ControlEvent
import com.network.system.node.Node
import com.network.system.Network
import com.network.packet.{DvPacket, NetworkPacket}
import com.network.system.state.{Idle, Running, State}
import com.network.util.{Ack, Periodic, Triggered}

import scala.concurrent.duration.{Duration, FiniteDuration}

abstract private[system] class Routing(node: Node, network: Network) {

  private var state: State = Idle
  private var currentTime: Duration = FiniteDuration(0, TimeUnit.SECONDS)
  private var _interfaces = Map.empty[Node, Interface]

  final private[routing] def connect(i: Interface): Unit =
    _interfaces += i.node -> i

  final protected def interfaces: Map[Node, Interface] = _interfaces

  final protected def init(): Unit = {
    println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node initializing")
    state = Running
    for {
      i <- interfaces.values
      _ = i.open()
    } yield route(DvPacket(node, 0))(i)
  }

  final protected def terminate(): Unit = {
    println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node terminating")
    for {
      i <- interfaces.values
      _ = i.close()
    } yield route(DvPacket(node, Connection.CLOSED))(i)
    state = Idle
  }

  final private[routing] def incoming(nwp: NetworkPacket)(i: Interface): Unit = state match {
    case Running =>
      currentTime = nwp.elapsedTime
      println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node receiving ${nwp.dvPacket} from ${i.node}")
      receive(nwp.dvPacket)(i)
    case Idle =>
  }

  final protected def schedulePeriodic(delay: Duration, period: Duration)(event: => Unit): Unit = {

    def update(elapsedTime: Duration): Unit = {
      val control = Control[Ack]()
        .filter(_ => state == Running)
        .andThen(_ => currentTime = elapsedTime)
        .andThen(_ => event)
        .andThen(ack => update(ack.time + period))

      publish(ControlEvent(control, elapsedTime, Periodic))
    }

    update(delay + period)
  }

  final protected def scheduleOnce(time: Duration)(event: => Unit): Unit = {
    val control = Control[Ack]()
        .andThen(_ => currentTime = time)
        .andThen(_ => event)

    publish(ControlEvent(control, time, Triggered))
  }

  final protected def route(dvp: DvPacket)(i: Interface): Unit = {
    val _state = state
    val control = Control[Ack]()
      .filter(_ => _state == Running)
      .andThen(ack => i.send(NetworkPacket(dvp, ack.time)))

    println(s"TIME ${"%10d".format(currentTime.toMicros)}: $node routing $dvp to ${i.node}")

    publish(ControlEvent(control, currentTime + i.link.delay, Triggered))
  }

  final private def publish(event: ControlEvent): Unit = {
    network.publish(event)
  }

  protected def receive(dvp: DvPacket)(i: Interface): Unit

}
