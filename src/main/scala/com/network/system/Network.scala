package com.network.system

import java.util.concurrent.TimeUnit

import com.network.system.routing.connection.Link
import com.network.system.router.Router
import com.network.event.ControlEvent
import com.network.system.node.Node
import com.network.util.{Ack, Triggered}

import scala.annotation.tailrec
import scala.collection.{mutable => m}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Success, Try}

class Network {

  implicit val ord: Ordering[ControlEvent] =
    Ordering.by((_: ControlEvent).elapsedTime).reverse
  private val events = m.PriorityQueue[ControlEvent]()
  private val table = m.Map.empty[Node, Router]

  final def connect(node1: Node, node2: Node)(link: Link): Unit = {
    val router1 = table.getOrElseUpdate(node1, Router(node1, this))
    val router2 = table.getOrElseUpdate(node2, Router(node2, this))

    link.connect(router1, router2)
  }

  final def init(f: Node => FiniteDuration = _ => Duration.Zero): Unit = for {
    (node, router) <- table
  } yield router.run(f(node))

  final def process(): Duration = {

    @tailrec
    def process(elapsedTime: Duration): Duration = Try(events.dequeue()) match {
      case Success(event) if events.exists(_.reason == Triggered) =>
        event.control.process(Ack(event.elapsedTime))
        process(event.elapsedTime)
      case _ => elapsedTime
    }

    process(FiniteDuration(0, TimeUnit.SECONDS))
  }

  final def scheduleStart(node:Node)(time: FiniteDuration): Unit = {
    table.get(node).foreach(
      _.run(time))
  }

  final def scheduleShutdown(node: Node)(time: FiniteDuration): Unit = {
    table.get(node).foreach(
      _.shutdown(time))
  }

  final private[system] def publish(event: ControlEvent): Unit =
    events.enqueue(event)

  override final def toString: String = {
    table.values
      .toList.sortBy[String](_.node.id)(Ordering.String)
      .mkString("\n")
  }

}
