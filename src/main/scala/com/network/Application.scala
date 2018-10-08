package com.network

import java.util.concurrent.TimeUnit

import com.network.connection.Link
import com.network.system.Network
import com.network.system.node.Node

import scala.concurrent.duration.FiniteDuration

object Application extends App {

  val network = new Network()

  val router0 = network.routerOf(Node("0"))
  val router1 = network.routerOf(Node("1"))
  val router2 = network.routerOf(Node("2"))
  val router3 = network.routerOf(Node("3"))
  val router4 = network.routerOf(Node("4"))

  router3.shutdown(FiniteDuration(300000, TimeUnit.MICROSECONDS))
  router2.shutdown(FiniteDuration(200000, TimeUnit.MICROSECONDS))
  //router3.run(FiniteDuration(400000, TimeUnit.MICROSECONDS))
  //router2.run(FiniteDuration(400000, TimeUnit.MICROSECONDS))

  network.connect(router4, router1)(Link(532, 0.076792222))
  network.connect(router4, router2)(Link(669, 0.133467327))
  network.connect(router3, router2)(Link(722, 0.051783073))
  network.connect(router3, router4)(Link(196, 0.184216793))
  network.connect(router0, router1)(Link(907, 0.125563734))
  network.connect(router0, router2)(Link(291, 0.217457185))
  network.connect(router1, router3)(Link(24, 0.208844158))

  network.init()
  println(network)

  network.start()

  println(s"converged at time ${network.process()}")

  println(network)

}
