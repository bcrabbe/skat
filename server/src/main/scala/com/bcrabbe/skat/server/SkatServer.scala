package com.bcrabbe.skat.server;

import akka.actor.ActorSystem
import akka.routing.FromConfig

object SkatServer {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("SkatServer")
    system.actorOf(LobbyActor.props(), "lobby")
  }
}
