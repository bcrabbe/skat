package com.yalingunayer.bastra.server

import akka.actor.Actor
import akka.actor.Props
import com.yalingunayer.bastra.commons.domain.Player
import com.yalingunayer.bastra.commons.Messages
import akka.actor.ActorRef
import com.yalingunayer.bastra.commons.domain.GameRoom
import com.yalingunayer.bastra.commons.Utils
import akka.actor.Terminated

object LobbyActor {
  def props() = Props(classOf[LobbyActor])
}

class LobbyActor extends Actor {
  var games: Map[ActorRef, GameSession] = Map()
  var players: Map[ActorRef, PlayerSession] = Map()
  var waiting: List[PlayerSession] = List()
  val playersPerGame = 3

  def tryMakeMatch = waiting.length match {
    case enoughPlayers: Int if enoughPlayers >= playersPerGame => {
      val playersForGame = waiting.take(playersPerGame)

      val room = new GameRoom(name = Utils.uuid)
      val roomRef = context.system.actorOf(GameRoomActor.props(room))

      waiting = waiting.drop(playersPerGame)

      println(f"Players ${playersForGame} are matched and will promptly join the room ${room}")

      roomRef ! GameRoomActor.Messages.ReceivePlayers(playersForGame)
    }
    case _ => println("Not enough players to make a match")
  }

  def receive: Receive = {
    case Messages.Server.Connect(player: Player) => {
      println(f"Player connected: ${player}")

      sender ! Messages.Server.Connected()

      context.watch(sender)

      val session = PlayerSession(player, sender)
      players = players + (sender -> session)
      waiting = waiting :+ session
      tryMakeMatch
    }

    case Terminated(ref: ActorRef) if players.isDefinedAt(ref) => {
      // a player has disconnected
      val session = players.get(ref).get

      // remove them from the players list
      players = players - ref

      // and also from the waiting queue (if present)
      waiting = Utils.removeLast(waiting, session)

      println(f"Player has disconnected: ${session.player}")
    }
  }

  override def preStart() = {
    println("Server is now ready to accept connections")
  }
}
