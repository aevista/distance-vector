package com.network.packet

import scala.concurrent.duration.FiniteDuration

case class NetworkPacket(dvPacket: DvPacket, elapsedTime: FiniteDuration)
