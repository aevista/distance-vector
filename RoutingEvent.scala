package com.network.event

import com.network.control.Control
import com.network.util.{Ack, Reason}

import scala.concurrent.duration.FiniteDuration

case class RoutingEvent(control: Control[Ack, Ack], elapsedTime: FiniteDuration, reason: Reason)

