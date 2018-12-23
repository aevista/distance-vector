package com.network.system.routing.connection

import com.network.system.node.Node
import com.network.packet.NetworkPacket

trait Interface {

  def node: Node
  def link: Link
  private[connection] def bind(): Unit
  private[routing] def receive(nwp: NetworkPacket): Unit
  private[routing] def send(nwp: NetworkPacket): Unit
  private[routing] def close(): Unit
  private[routing] def open(): Unit

  override def toString: String = s"Interface(${node.id})"
}
