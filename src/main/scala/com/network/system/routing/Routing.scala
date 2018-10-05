package com.network.system.routing

import java.util.concurrent.TimeUnit

import com.network.connection.EndPoint
import com.network.control.Control
import com.network.event.RoutingEvent
import com.network.manager.Network
import com.network.packet.{DvPacket, NetworkPacket}
import com.network.util.{Ack, Periodic, Reason, Triggered}

import scala.concurrent.duration.{Duration, FiniteDuration}

abstract class Routing(network: Network) {

  private var currentTime: Duration = FiniteDuration(0, TimeUnit.SECONDS)

  final def incoming(packet: NetworkPacket)(endPoint: EndPoint): Unit = {
    currentTime = packet.elapsedTime
    receive(packet.dvPacket)(endPoint)
  }

  final protected def schedule(time: Duration)(event: => Unit): Unit = {

    def update(elapsedTime: Duration): Unit = {
      val control = Control[Ack]()
        .andThen(_ => event)
        .andThen(ack => update(ack.time + time))

      publish(control, elapsedTime, Periodic)
    }

    update(time)
  }

  final protected def scheduleOnce(time: Duration)(event: => Unit): Unit = {
    publish(Control().andThen(_ => event), time, Triggered)
  }

  final protected def route(packet: DvPacket)(endPoint: EndPoint): Unit = {
    val control = Control[Ack]()
      .andThen(ack => endPoint.send(NetworkPacket(packet, ack.time)))

    publish(control, currentTime + endPoint.link.delay, Triggered)
  }

  final private def publish(control: Control[Ack, Ack], time: Duration, reason: Reason): Unit = {
    network.publish(RoutingEvent(control, time, reason))
  }

  protected def receive(packet: DvPacket)(endPoint: EndPoint): Unit

}
