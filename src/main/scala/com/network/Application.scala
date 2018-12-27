package com.network

import java.util.concurrent.TimeUnit

import com.network.system.node.Node
import com.network.system.routing.connection.Link
import com.network.system.Network

import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.{Failure, Success, Try}

object Application extends App {

  val network = new Network()

  val Pattern = """([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)""".r

  for {
    file <- Try(Source.fromFile(args.head)).toOption.toSeq
    line <- file.getLines().toList
  } yield line match {
    case Pattern(id1, id2, w, d) => Try((id1.toInt, id2.toInt, w.toInt, d.toDouble)) match {
      case Success((a, b, weight, delay)) => network.connect(Node(a), Node(b))(Link(weight, delay))
      case Failure(e) => println(s"Failed to create connection $e")
    }
    case pattern => println(s"Failed to match Pattern $pattern")
  }

  network.scheduleShutdown(Node(3))(Duration(1000, TimeUnit.MICROSECONDS))
  network.scheduleShutdown(Node(1))(Duration(2000, TimeUnit.MICROSECONDS))

  network.init()

  val convergedTime = network.process()

  println(s"Converged at time $convergedTime")

  println(network)

}
