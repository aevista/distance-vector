package com.network.manager

import java.util.concurrent.TimeUnit

import com.network.connection.Link
import com.network.system.node.{Node, Router}
import com.network.event.ControlEvent
import com.network.util.{Ack, Triggered}

import scala.annotation.tailrec
import scala.collection.{mutable => m}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Success, Try}

class Network {

  implicit val ord: Ordering[ControlEvent] = Ordering.by((_: ControlEvent).elapsedTime).reverse
  private val events = m.PriorityQueue[ControlEvent]()

  private val table = m.Map.empty[Router, m.Map[Router, Link]]
    .withDefaultValue(m.Map.empty[Router, Link])

  final def routerOf(node: Node): Router = Router(node, this)

  final def connect(router1: Router, router2: Router)(link: Link): Unit = {
    table.update(router1, table(router1).updated(router2, link))
  }

  final def publish(event: ControlEvent): Unit = {
    println(s"published event $event")
    events.enqueue(event)
  }

  final def initNetwork(): Unit =  for {
    (router1, entries) <- table
    (router2, link) <- entries
  } yield link.connect(router1, router2)

  final def startNetwork(): Unit = for {
    (router1, entries) <- table
    (router2, _) <- entries
    r <- Set(router1, router2)
  } yield r.run()

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

  override def toString: String = {
    table.flatMap { case (r1, e) => e.keys.toSet + r1 }
      .toSet.toList.sortBy[String](_.node.id)(Ordering.String)
      .mkString("\n")
  }

}
