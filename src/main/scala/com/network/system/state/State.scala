package com.network.system.state

private[system] sealed trait State
private[system] case object Idle extends State
private[system] case object Running extends State
