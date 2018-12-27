package com.network.system

import java.util.concurrent.TimeUnit

import com.network.system.routing.connection.Link
import com.network.system.router.Router
import com.network.event.ControlEvent
import com.network.system.node.Node
import com.network.util.{Ack, Reason, Triggered}

import scala.annotation.tailrec
import scala.collection.{mutable => m}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Success, Try}

class Network {

  val ord: Ordering[ControlEvent] = {
    type CE = ControlEvent
    implicit val reasonOrd: Ordering[Reason] =
      Ordering.by[Reason, String](_.toString).reverse

    Ordering.by[CE, (Duration, Reason)](e => (e.elapsedTime, e.reason)).reverse
  }

  private val events = m.PriorityQueue[ControlEvent]()(ord)
  private val table = m.Map.empty[Node, Router]

  final def connect(node1: Node, node2: Node)(link: Link): Unit = {
    link.connect(
      table.getOrElseUpdate(node1, Router(node1, this)),
      table.getOrElseUpdate(node2, Router(node2, this)))
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
      .toList.sortBy[Int](_.node.id)(Ordering.Int)
      .mkString("\n")
  }

}
