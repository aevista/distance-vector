package com.network.connection.state

sealed trait State
case object Closed extends State
case object Opened extends State