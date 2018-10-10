package com.network.connection

import com.network.system.node.Node
import com.network.packet.NetworkPacket

trait Interface {

  def node: Node
  def link: Link
  private[connection] def bind(): Unit
  def receive(packet: NetworkPacket): Unit
  def send(packet: NetworkPacket): Unit
  def close(): Unit
  def open(): Unit

  override def toString: String = s"EndPoint(${node.id})"
}
