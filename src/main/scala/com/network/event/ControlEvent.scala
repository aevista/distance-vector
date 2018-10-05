package com.network.event

import com.network.control.Control
import com.network.util.{Ack, Reason}

import scala.concurrent.duration.Duration

case class ControlEvent(control: Control[Ack, Ack], elapsedTime: Duration, reason: Reason)

