package com.network.connection

import com.network.connection.state.{Closed, Opened, State}
import com.network.system.node.{Node, Router}
import com.network.packet.{DvPacket, NetworkPacket}

object Connection {

  val CLOSED: Int = -999999

  def apply(router1: Router, router2: Router)(link: Link): Connection =
    new Connection(router1, router2, link)
}

class Connection(router1: Router, router2: Router, link: Link) {

  var (state1, state2) = (Closed: State, Closed: State)

  new Interface { interface1 =>

    def node: Node = router2.node
    def link: Link = Connection.this.link
    private[connection] def bind(): Unit = router1.connect(this)
    def receive(packet: NetworkPacket): Unit = state1 match {
      case Opened => router1.incoming(packet)(this)
      case Closed =>
    }
    def send(packet: NetworkPacket): Unit =  interface2.receive(packet)
    def close(): Unit = state1 = Closed
    def open(): Unit = state1 = Opened

    private val interface2 = new Interface {
      def node: Node = router1.node
      def link: Link = Connection.this.link
      private[connection] def bind(): Unit = router2.connect(this)
      def receive(packet: NetworkPacket): Unit = state2 match {
        case Opened => router2.incoming(packet)(this)
        case Closed =>
      }
      def send(packet: NetworkPacket): Unit = interface1.receive(packet)
      def close(): Unit = state2 = Closed
      def open(): Unit = state2 = Opened
    }

    println(s"connecting ${router1.node.id} to ${router2.node.id}")

    interface1.bind()
    interface2.bind()
  }

  override def toString: String =
    s"Connection(${router1.node.id} <-> ${router2.node.id})"

}


