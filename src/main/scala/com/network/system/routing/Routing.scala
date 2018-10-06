package com.network.system.routing

import java.util.concurrent.TimeUnit

import com.network.connection.EndPoint
import com.network.control.Control
import com.network.event.ControlEvent
import com.network.system.Network
import com.network.packet.{DvPacket, NetworkPacket}
import com.network.system.node.Node
import com.network.util.{Ack, Periodic, Reason, Triggered}

import scala.concurrent.duration.{Duration, FiniteDuration}

abstract class Routing(network: Network) {

  private var routes = Map.empty[Node, EndPoint]
  private var currentTime: Duration = FiniteDuration(0, TimeUnit.SECONDS)

  final def connect(endPoint: EndPoint): Unit = {
    routes += endPoint.node -> endPoint
  }

  final def incoming(packet: NetworkPacket)(endPoint: EndPoint): Unit = {
    currentTime = packet.elapsedTime
    receive(packet.dvPacket)(endPoint)
  }

  final protected def schedulePeriodic(period: Duration)(event: => Unit): Unit = {

    def update(elapsedTime: Duration): Unit = {
      val control = Control[Ack]()
        .andThen(_ => event)
        .andThen(ack => update(ack.time + period))

      publish(control, elapsedTime, Periodic)
    }

    update(period)
  }

  final protected def scheduleOnce(delay: Duration)(event: => Unit): Unit = {
    publish(Control().andThen(_ => event), delay, Triggered)
  }

  final protected def route(packet: DvPacket)(endPoint: EndPoint): Unit = {
    val control = Control[Ack]()
      .andThen(ack => endPoint.send(NetworkPacket(packet, ack.time)))

    publish(control, currentTime + endPoint.link.delay, Triggered)
  }

  final private def publish(control: Control[Ack, Ack], time: Duration, reason: Reason): Unit = {
    network.publish(ControlEvent(control, time, reason))
  }

  final protected def endPoints: Map[Node, EndPoint] = routes

  protected def receive(packet: DvPacket)(endPoint: EndPoint): Unit

}
