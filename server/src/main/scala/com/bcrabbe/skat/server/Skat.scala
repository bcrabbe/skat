package com.bcrabbe.skat.server

import com.bcrabbe.skat.server.lobby.LobbyActor
import akka.actor.ActorSystem

object SkatServer {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("SkatServer")
    system.actorOf(LobbyActor.props(), "lobby")
  }
}
