package com.network

import com.network.system.node.Node
import com.network.system.routing.connection.Link
import com.network.system.Network

import scala.io.Source
import scala.util.{Failure, Success, Try}

object Application extends App {

  val network = new Network()

  val Relation = """([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)""".r

  for {
    file <- Try(Source.fromFile(args.head)).toOption.toSeq
    line <- file.getLines().toList
  } yield  line match {
    case Relation(id1, id2, w, d) => Try(Link(w.toInt, d.toDouble)) match {
      case Success(link) => network.connect(Node(id1), Node(id2))(link)
      case Failure(e) => println(s"Failed to create connection $e")
    }
    case rel => println(s"Failed to match Relation $rel")
  }

  network.init()

  val convergedTime = network.process()

  println(s"Converged at time $convergedTime")

  println(network)

}
