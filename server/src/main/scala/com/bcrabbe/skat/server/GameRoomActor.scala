package com.bcrabbe.skat.server

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Terminated
import akka.remote.DisassociatedEvent
import com.bcrabbe.skat.common.Messages
import com.bcrabbe.skat.common.domain._
import scala.util.Failure
import scala.util.Random
import scala.util.Success
import scala.util.Try

object GameRoomActor {

  case class GameState(skat: CardStack, players: List[PlayerInfo])

  case class StateResult(newState: GameRoomActor.GameState, winner: Option[PlayerInfo], isFished: Boolean)

  case class PlayerInfo(session: PlayerSession, state: PlayerState)

  object Messages {
    case class ReceivePlayers(player: List[PlayerSession])
  }

  def props(room: GameRoom) = Props(classOf[GameRoomActor], room)

}

class GameRoomActor(val room: GameRoom) extends Actor {
  var playerActorMap: Map[ActorRef, PlayerSession] = Map()
  def receive = initializing

  /**
   * The inital state. Wait for the matchmaking server to introduce the players.
   */
  def initializing: Receive = {
    case GameRoomActor.Messages.ReceivePlayers(playersForGame: List[PlayerSession]) => {
      //store player refs
      val initAcc: Map[ActorRef, PlayerSession] = Map()
      playerActorMap = playersForGame.foldLeft(initAcc)(
        (acc, player) => {
          context.watch(player.ref)
          acc + (player.ref -> player)
        }
      )
      //notify players of opponents
      playersForGame.foreach((playerSession) => {
        playerSession.ref ! Messages.Game.Joined(room)
        val opponents: List[Player] = playersForGame.filter((p) => p != playerSession).map(_.player)
        playerSession.ref ! Messages.Game.SetUp(opponents)
      })
      //set the bidding roles for first game
      val initPlayerStates: List[PlayerInfo] = playersForGame.zip(BiddingRole.all).map {
        case (playerSession: PlayerSession, biddingRole: BiddingRole) => PlayerInfo(playerSession, PlayerState(PlayerScore(0), biddingRole, Option.empty, CardStack.empty))
      }
      deal(initPlayerStates)
    }
  }

  /**
   * Start a new hand.
   */
  def deal(players: List[PlayerInfo]) = {
    val deck = CardStack.shuffled.cards
    val skat: List[CardStack] = deck.deal(2)
    val hands: List[CardStack] = List(deck.deal(10), deck.deal(10), deck.deal(10))
    val playersWithHands: List[PlayerInfo] = players.zip(hands).map {
      case (player: PlayerInfo, hand: CardStack) => player.copy(state = player.state.copy(hand = hand))
    }
    playersWithHands.foreach((player) => player.session.ref ! Messages.Game.CardsDelt(player.state.hand))
    val initialState = GameRoomActor.GameState(BiddingPhase, skat, playersWithHands)
    context.become(bidding(initialState))
    println(s"Starting bidding between $playersWithHands")
    //send initial bidding message
    val zaagenPlayer = initialState.players.find((player) => player.state.biddingRole == Zaagen)
    zaagenPlayer.ref ! Messages.Game.Bidding.Speaking(Scores.biddingScores(0))
  }

   /**
   * Conduct bidding.
   */
  def bidding(state: GameRoomActor.GameState) = {

  }

  /**
   * The game is being played.
   */
  def playing(state: GameRoomActor.GameState): Receive = {
    case t: Messages.Game.Terminate => {
      println(f"Terminating the game ${room.name} due to ${t.reason}")

      state.playerInTurn.session.ref ! t
      state.playerWaiting.session.ref ! t

      self ! PoisonPill
    }

    case Terminated(ref: ActorRef) if players.isDefinedAt(ref) => onPlayerLeft(ref)
    case Messages.Game.Leave() if players.isDefinedAt(sender) => onPlayerLeft(sender)
  }

  /**
   * The game is finished. Wait for rematch responses.
   */
  def finished(pending: Set[ActorRef]): Receive = {
    case Messages.Player.Accept() if playerActorMap.isDefinedAt(sender) => {
      val newPending = pending - sender
      val player = playerActorMap(sender)
      println(f"Player ${player.player} has agreed to rematch")

      if (newPending.isEmpty) {
        println("Restarting game...")
        val sessions = playerActorMap.values

        sessions.foreach(player => player.ref ! Messages.Game.Restart)

        deal(sessions.head, sessions.tail.head, sessions.tail.tail.head)
      } else {
        println(f"Pending ${newPending.size} more replies")
        context.become(finished(newPending))
      }
    }

    case Messages.Player.Refuse() if playerActorMap.isDefinedAt(sender) => {
      val player = playerActorMap(sender)
      println(f"Player ${player.player} has refused to rematch")
      onPlayerLeft(sender)
    }
  }

  /**
   * A player has left. Terminate the game.
   */
  def onPlayerLeft(ref: ActorRef): Unit = {
    val session = players.get(ref).get
    println(f"Player ${session.player} has left the game.")
    self ! Messages.Game.Terminate(session.player)
  }
}
