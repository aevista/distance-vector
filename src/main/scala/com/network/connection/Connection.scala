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

  var state1: State = Opened
  var state2: State = Opened

  new EndPoint { endPoint1 =>

    def node: Node = router2.node
    def link: Link = Connection.this.link
    def bind(): Unit = router1.init(this)
    def close(time: Duration): Unit = (state1, state2) match {
      case (Closed, _) | (_, Closed) =>
      case _ =>
        send(NetworkPacket(DvPacket(router1.node, Connection.CLOSED), time))
        state1 = Closed
        println(s"closed $this")
    }
    def receive(packet: NetworkPacket): Unit = router1.incoming(packet)(this)
    def send(packet: NetworkPacket): Unit = (state1, state2) match {
      case (Closed, _) | (_, Closed) =>
        receive(NetworkPacket(DvPacket(node, Connection.CLOSED), packet.elapsedTime))
      case _ =>
        endPoint2.receive(packet)
    }

    private val endPoint2 = new EndPoint {
      def node: Node = router1.node
      def link: Link = Connection.this.link
      def bind(): Unit = router2.init(this)
      def close(time: Duration): Unit = (state1, state2) match {
        case (Closed, _) | (_, Closed) =>
        case _ =>
          send(NetworkPacket(DvPacket(router2.node, Connection.CLOSED), time))
          state2 = Closed
          println(s"closed $this")
      }
      def receive(packet: NetworkPacket): Unit = router2.incoming(packet)(this)
      def send(packet: NetworkPacket): Unit = (state1, state2) match {
        case (Closed, _) | (_, Closed) =>
          receive(NetworkPacket(DvPacket(node, Connection.CLOSED), packet.elapsedTime))
        case _ =>
          endPoint1.receive(packet)
      }
    }

    println(s"connecting ${router1.node.id} to ${router2.node.id}")

    endPoint1.bind()
    endPoint2.bind()
  }

  override def toString: String =
    s"Connection(${router1.node.id} <-> ${router2.node.id})"

}


