package com.network.system.routing.connection.state

private[connection] sealed trait State
private[connection] case object Closed extends State
private[connection] case object Opened extends State