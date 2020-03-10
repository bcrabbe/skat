package com.bcrabbe.skat.server;

import akka.actor.ActorSystem
import akka.routing.FromConfig

object Skat {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("skat-server")
    system.actorOf(LobbyActor.props(), "lobby")
  }
}
