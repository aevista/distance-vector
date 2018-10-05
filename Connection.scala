package com.network.connection

import com.network.connection.state.{Closed, Opened, State}
import com.network.node.{Node, Router}
import com.network.packet.{DvPacket, NetworkPacket}

import scala.concurrent.duration.FiniteDuration

object Connection {
  val CLOSED: Int = Int.MaxValue

  def apply(router1: Router, router2: Router)(link: Link): Connection =
    new Connection(router1, router2, link)
}

class Connection(router1: Router, router2: Router, link: Link) {

  var state: State = Opened

  new EndPoint { endPoint1 =>

    def node: Node = router2.node
    def link: Link = Connection.this.link
    def bind(): Unit = router1.init(this)
    def close(time: FiniteDuration): Unit = {
      send(NetworkPacket(DvPacket(node.id, Connection.CLOSED), time))
      state = Closed
    }
    def receive(packet: NetworkPacket): Unit = router1.incoming(packet)(this)
    def send(packet: NetworkPacket): Unit = state match {
      case Opened => endPoint2.receive(packet)
      case Closed =>
    }

    private val endPoint2 = new EndPoint {
      def node: Node = router1.node
      def link: Link = Connection.this.link
      def bind(): Unit = router2.init(this)
      def close(time: FiniteDuration): Unit = {
        send(NetworkPacket(DvPacket(node.id, Connection.CLOSED), time))
        state = Closed
      }
      def receive(packet: NetworkPacket): Unit = router2.incoming(packet)(this)
      def send(packet: NetworkPacket): Unit = state match {
        case Opened => endPoint1.receive(packet)
        case Closed =>
      }
    }

    println(s"connecting ${router1.node.id} to ${router2.node.id}")


    endPoint1.bind()
    endPoint2.bind()
  }

}

