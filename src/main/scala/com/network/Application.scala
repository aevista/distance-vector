package com.network

import java.util.concurrent.TimeUnit

import com.network.system.node.Node
import com.network.system.routing.connection.Link
import com.network.system.Network

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object Application extends App {

  val network = new Network()

  val node0 = Node("0")
  val node1 = Node("1")
  val node2 = Node("2")
  val node3 = Node("3")
  val node4 = Node("4")

  network.connect(node4, node1)(Link(532, 0.076792222))
  network.connect(node4, node2)(Link(669, 0.133467327))
  network.connect(node3, node2)(Link(722, 0.051783073))
  network.connect(node3, node4)(Link(196, 0.184216793))
  network.connect(node0, node1)(Link(907, 0.125563734))
  network.connect(node0, node2)(Link(291, 0.217457185))
  network.connect(node1, node3)(Link(24, 0.208844158))

  Random.setSeed(1)

  network.init(_ =>
    FiniteDuration(Random.nextInt(100000), TimeUnit.MICROSECONDS))

  network.scheduleShutdown(node3)(FiniteDuration(1300000, TimeUnit.MICROSECONDS))
  network.scheduleShutdown(node1)(FiniteDuration(1500000, TimeUnit.MICROSECONDS))
  network.scheduleStart(node3)(FiniteDuration(1800000, TimeUnit.MICROSECONDS))

  val convergedTime = network.process()

  println(s"Converged at time $convergedTime")

  println(network)

}
