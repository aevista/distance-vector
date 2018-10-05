package com.network.packet

import scala.concurrent.duration.Duration

case class NetworkPacket(dvPacket: DvPacket, elapsedTime: Duration)
