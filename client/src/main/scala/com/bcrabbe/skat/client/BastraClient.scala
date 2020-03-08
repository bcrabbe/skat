package com.bcrabbe.skat.client;

import akka.actor.ActorSystem

object SkatClient {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("SkatClient")
    system.actorOf(PlayerActor.props)
  }
}
