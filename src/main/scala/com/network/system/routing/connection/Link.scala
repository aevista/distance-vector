package com.network.system.routing.connection

import java.util.concurrent.TimeUnit

import com.network.system.router.Router

import scala.concurrent.duration.FiniteDuration

object Link {
  def apply(weight: Int, delay: Double): Link =
    Link(weight, FiniteDuration((delay * 1000000).toLong, TimeUnit.MICROSECONDS))
}

case class Link(weight: Int, delay: FiniteDuration) {

  final private[system] def connect(router1: Router, router2: Router): Connection =
    Connection(router1, router2)(this)

}
