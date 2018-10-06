package com.network.connection

import com.network.connection.state.{Closed, Opened, State}
import com.network.system.node.{Node, Router}
import com.network.packet.{DvPacket, NetworkPacket}

import scala.concurrent.duration.Duration

object Connection {

  val CLOSED: Int = -999999

  def apply(router1: Router, router2: Router)(link: Link): Connection =
    new Connection(router1, router2, link)
}

class Connection(router1: Router, router2: Router, link: Link) {

  var state1: State = Closed
  var state2: State = Closed

  new EndPoint { endPoint1 =>

    def node: Node = router2.node
    def link: Link = Connection.this.link
    private[connection] def bind(): Unit = router1.connect(this)
    def receive(packet: NetworkPacket): Unit = router1.incoming(packet)(this)
    def send(packet: NetworkPacket): Unit = state2 match {
      case Closed =>
        receive(NetworkPacket(DvPacket(node, Connection.CLOSED), packet.elapsedTime))
      case _ =>
        endPoint2.receive(packet)
    }
    def close(): Unit = state1 match {
      case Closed =>
      case _ =>
        state1 = Closed
        println(s"closed $this")
    }
    def open(): Unit = state1 = Opened

    private val endPoint2 = new EndPoint {
      def node: Node = router1.node
      def link: Link = Connection.this.link
      private[connection] def bind(): Unit = router2.connect(this)
      def receive(packet: NetworkPacket): Unit = router2.incoming(packet)(this)
      def send(packet: NetworkPacket): Unit = state1 match {
        case Closed =>
          receive(NetworkPacket(DvPacket(node, Connection.CLOSED), packet.elapsedTime))
        case _ =>
          endPoint1.receive(packet)
      }
      def close(): Unit = state2 match {
        case Closed =>
        case _ =>
          state2 = Closed
          println(s"closed $this")
      }
      def open(): Unit = state2 = Opened
    }

    println(s"connecting ${router1.node.id} to ${router2.node.id}")

    endPoint1.bind()
    endPoint2.bind()
  }

  override def toString: String =
    s"Connection(${router1.node.id} <-> ${router2.node.id})"

}


