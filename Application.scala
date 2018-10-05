package com.network

import com.network.connection.Link
import com.network.manager.Network
import com.network.node.Node

object Application extends App {

  val network = new Network()

  val router0 = network.routerOf(Node("0"))
  val router1 = network.routerOf(Node("1"))
  val router2 = network.routerOf(Node("2"))
  val router3 = network.routerOf(Node("3"))
  val router4 = network.routerOf(Node("4"))

  network.connect(router4, router1)(Link(532, 0.076792222))
  network.connect(router4, router2)(Link(669, 0.133467327))
  network.connect(router3, router2)(Link(722, 0.051783073))
  network.connect(router3, router4)(Link(196, 0.184216793))
  network.connect(router0, router1)(Link(907, 0.125563734))
  network.connect(router0, router2)(Link(291, 0.217457185))
  network.connect(router1, router3)(Link(24, 0.208844158))


  network.initNetwork()
  network.startNetwork()

  println(s"converged at time ${network.process()}s")

  println(network)

}
