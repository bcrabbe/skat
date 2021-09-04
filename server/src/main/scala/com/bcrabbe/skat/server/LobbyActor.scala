package com.bcrabbe.skat.server.lobby

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import com.bcrabbe.skat.common.Messages
import com.bcrabbe.skat.common.Utils
import com.bcrabbe.skat.common.domain.GameRoom
import com.bcrabbe.skat.common.domain.Player
import com.bcrabbe.skat.server.session.GameSession
import com.bcrabbe.skat.server.session.PlayerSession
import com.bcrabbe.skat.server.game.GameRoomActor

object LobbyActor {
  def props() = Props(classOf[LobbyActor])
}

class LobbyActor extends Actor {
  var games: Map[ActorRef, GameSession] = Map()
  var players: Map[ActorRef, PlayerSession] = Map()
  var waiting: List[PlayerSession] = List()
  val playersPerGame = 3

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

  def startRoom(room: GameRoom): ActorRef = {
    context.system.actorOf(GameRoomActor.props(room))
  }

  def tryMakeMatch = waiting.length match {
    case enoughPlayers: Int if enoughPlayers >= playersPerGame => {
      val playersForGame = waiting.take(playersPerGame)
      val room = new GameRoom(name = s"Game room ${games.size}")
      val roomRef = this.startRoom(room)
      waiting = waiting.drop(playersPerGame)
      println(f"Players ${playersForGame} are matched and will promptly join the room ${room}")
      roomRef ! GameRoomActor.Messages.ReceivePlayers(playersForGame)
    }
    case _ => println("Not enough players to make a match")
  }
}
