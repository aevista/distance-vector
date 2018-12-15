package com.network.system.routing.connection

import com.network.system.node.Node
import com.network.system.routing.connection.state.{Closed, Opened, State}
import com.network.system.router.Router
import com.network.packet.NetworkPacket

object Connection {

  val CLOSED: Int = -999999

  private[connection] def apply(r1: Router, r2: Router)(link: Link): Connection =
    new Connection(r1, r2, link)
}

private[connection] class Connection(r1: Router, r2: Router, link: Link) {

  var (state1, state2) = (Closed: State, Closed: State)

  new Interface { i1 =>
    def node: Node = r2.node
    def link: Link = Connection.this.link
    private[connection] def bind(): Unit = r1.connect(this)
    private[routing]  def receive(nwp: NetworkPacket): Unit = state1 match {
      case Opened => r1.incoming(nwp)(this)
      case Closed =>
    }
    private[routing] def send(nwp: NetworkPacket): Unit =  i2.receive(nwp)
    private[routing] def close(): Unit = state1 = Closed
    private[routing] def open(): Unit = state1 = Opened

    private val i2 = new Interface {
      def node: Node = r1.node
      def link: Link = Connection.this.link
      private[connection] def bind(): Unit = r2.connect(this)
      private[routing] def receive(nwp: NetworkPacket): Unit = state2 match {
        case Opened => r2.incoming(nwp)(this)
        case Closed =>
      }
      private[routing] def send(nwp: NetworkPacket): Unit = i1.receive(nwp)
      private[routing] def close(): Unit = state2 = Closed
      private[routing] def open(): Unit = state2 = Opened
    }

    println(s"Connecting ${r1.node.id} to ${r2.node.id}")

    i1.bind()
    i2.bind()
  }

  override def toString: String =
    s"Connection(${r1.node.id} <-> ${r2.node.id})"

}


