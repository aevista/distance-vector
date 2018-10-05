package com.network.connection

import com.network.node.Node
import com.network.packet.NetworkPacket

import scala.concurrent.duration.FiniteDuration

trait EndPoint {

  def node: Node
  def link: Link
  def bind(): Unit
  def receive(packet: NetworkPacket): Unit
  def send(packet: NetworkPacket): Unit
  def close(time: FiniteDuration): Unit

}
