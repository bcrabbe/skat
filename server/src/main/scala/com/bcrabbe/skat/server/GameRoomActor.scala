package com.bcrabbe.skat.server.game

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Terminated
import akka.remote.DisassociatedEvent
import com.bcrabbe.skat.server.session.PlayerSession
import com.bcrabbe.skat.common.Messages
import com.bcrabbe.skat.common.domain._
import scala.util.Failure
import scala.util.Random
import scala.util.Success
import scala.util.Try

object GameRoomActor {

  case class GameState(
    skat: CardStack,
    players: List[PlayerInfo],
    bidded: Option[Int] = None,
    gameType: Option[Game] = None,
    biddingWinner: Option[Player] = None
  )

  def getPlayersByRole(state: GameState): Map[BiddingRole, PlayerInfo] = state.players
   .groupBy(_.state.biddingRole)
   .map {
     case (role, playerList: List[PlayerInfo]) => (role, playerList(0))
   }

  case class StateResult(newState: GameRoomActor.GameState, winner: Option[PlayerInfo], isFished: Boolean)

  case class PlayerInfo(session: PlayerSession, state: PlayerState)

  object Messages {
    case class ReceivePlayers(player: List[PlayerSession])
  }

  def props(room: GameRoom) = Props(classOf[GameRoomActor], room)

}

class GameRoomActor(val room: GameRoom) extends Actor {
  import GameRoomActor.{ GameState, PlayerInfo, getPlayersByRole }

  var playerActorMap: Map[ActorRef, PlayerSession] = Map()
  def receive = initializing

  /**
    * The inital state. Wait for the matchmaking server to introduce the players.
    */
  def initializing: Receive = checkFailures(GameRoomActor.GameState(skat = CardStack.empty, players = List.empty)) orElse {
    case GameRoomActor.Messages.ReceivePlayers(playersForGame: List[PlayerSession]) => {
      //store player refs
      playerActorMap = playersForGame.foldLeft(Map[ActorRef, PlayerSession]())(
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
      val initPlayerStates: List[GameRoomActor.PlayerInfo] = playersForGame.zip(BiddingRole.all).map {
        case (playerSession: PlayerSession, biddingRole: BiddingRole) => GameRoomActor.PlayerInfo(
          playerSession,
          PlayerState(PlayerScore(0), biddingRole, Option.empty, CardStack.empty)
        )
      }
      deal(initPlayerStates)
    }
  }

  /**
    * Start a new hand.
    */
  def deal(players: List[GameRoomActor.PlayerInfo]) = {
    val deck: CardStack = CardStack.shuffled
    val skat: CardStack = deck.deal(2)
    val hands: List[CardStack] = List(deck.deal(10), deck.deal(10), deck.deal(10))
    val playersWithHands: List[GameRoomActor.PlayerInfo] = players.zip(hands).map {
      case (player: GameRoomActor.PlayerInfo, hand: CardStack) => player.copy(
        state = player.state.copy(
          hand = hand
        )
      )
    }
    println(s"Cards delt. Starting bidding between $playersWithHands")
    val initialState = GameRoomActor.GameState(skat, playersWithHands)
    playersWithHands.foreach(
      (player: GameRoomActor.PlayerInfo) => {
        player.session.ref ! Messages.Game.CardsDelt(player.state.hand)
      }
    )
    val playersByRole = getPlayersByRole(initialState)
    playersByRole(Zaagen).session.ref ! Messages.Game.Bidding.Roles.Speaking(playersByRole(Heuren).session.player)
    playersByRole(Heuren).session.ref ! Messages.Game.Bidding.Roles.Listening(playersByRole(Zaagen).session.player)
    playersByRole(Geeben).session.ref ! Messages.Game.Bidding.Roles.Waiting(playersByRole(Zaagen).session.player)
    context.become(expectBid(initialState))
  }

  /**
    * Conduct bidding.
    */
  def expectBid(state: GameRoomActor.GameState): Receive = {
    val playersByRole = getPlayersByRole(state)
    checkFailures(state) orElse {
      case m if isSentBy(Zaagen)(sender, state) => m match {
        case Messages.Game.Bidding.Bid(points) if points > state.bidded.getOrElse(0) => {
          println (s"Bid of $points from Zaagen player $sender")
          state.players
            .filterNot(p => p.state.biddingRole == Zaagen)
            .foreach(_.session.ref ! Messages.Game.Bidding.ObservedBid(playersByRole(Zaagen).session.player, points))
          context.become(expectBidResponse(state.copy(bidded = Some(points))))
        }
        case Messages.Game.Bidding.Pass() => {
          val points = state.bidded.getOrElse(Scores.biddingScores(0)._1)
          println(s"Zaagen player $sender passed on ${points}")
          state.players
            .filterNot(p => p.state.biddingRole == Zaagen)
            .foreach(_.session.ref ! Messages.Game.Bidding.ObservedPass(playersByRole(Zaagen).session.player, points))
          context.become(nextBiddingRoles(Zaagen, state))
        }
      }
      case m => {
        println(s"ignored $m from $sender - expecting bid")
      }
    }
  }

  def isSentBy(role: BiddingRole)(sender: ActorRef, state: GameRoomActor.GameState): Boolean = {
    Some(sender) == state.players.find(p => p.state.biddingRole == role).map(_.session.ref)
  }

  def expectBidResponse(state: GameRoomActor.GameState): Receive = checkFailures(state) orElse {
    case m if isSentBy(Heuren)(sender, state) => {
      val points = state.bidded.getOrElse(Scores.biddingScores(0)._1)
      val playersByRole = getPlayersByRole(state)
      m match {
        case Messages.Game.Bidding.AcceptBid() => {
          println(s"Heuren player accepted")
          state.players.
            filterNot(p => p.state.biddingRole == Heuren)
            .map(_.session.ref)
            .foreach(
              ref => ref ! Messages.Game.Bidding.ObservedAccept(playersByRole(Heuren).session.player, points)
            )
          context.become(expectBid(state))
        }
        case Messages.Game.Bidding.Pass() => {
          println(s"Heuren player $sender passed on ${state.bidded.getOrElse(Scores.biddingScores(0))}")
          state.players.filterNot(p => p.state.biddingRole == Heuren).map(_.session.ref).foreach(
            ref => ref ! Messages.Game.Bidding.ObservedPass(playersByRole(Heuren).session.player, points)
          )
          context.become(nextBiddingRoles(Heuren, state))
        }
      }
    }
  }

  def nextBiddingRoles(whoPassed: BiddingRole, state: GameRoomActor.GameState): Receive = {
    println(s"nextBiddingRoles")
    val newPlayers: List[PlayerInfo] = state.players.map(playerWhoPassed => playerWhoPassed match {
      case PlayerInfo(PlayerSession(_, ref), PlayerState(_, role, _, _)) if role == whoPassed => {
        playerWhoPassed.copy(state = playerWhoPassed.state.copy(biddingRole = Passed))
      }
    }).map(player => player match {
      case PlayerInfo(_, PlayerState(_, Zaagen, _, _)) => {
        player.copy(state = player.state.copy(biddingRole = Heuren))
      }
      case PlayerInfo(_, PlayerState(_, Geeben, _, _)) => {
        player.copy(state = player.state.copy(biddingRole = Zaagen))
      } // heuren will stay heuren
    })

    val newState = state.copy(players = newPlayers)
    println(s"nextBiddingRoles $newState")
    val playersByRole = getPlayersByRole(newState)
    playersByRole.get(Zaagen).map(
      _.session.ref ! Messages.Game.Bidding.Roles.Speaking(playersByRole(Heuren).session.player)
    )
    playersByRole.get(Heuren).map(
      _.session.ref ! Messages.Game.Bidding.Roles.Listening(playersByRole(Zaagen).session.player)
    )
    playersByRole.get(Passed).map(
      _.session.ref ! Messages.Game.Bidding.Roles.Waiting(playersByRole(Zaagen).session.player)
    )

    newPlayers.count(_.state.biddingRole == Passed) match {
      case 3 if state.bidded == None => ramsch(newState)
      case 2 if state.bidded.isDefined => whatAreWePlaying(newState)
      case _ => expectBid(newState)
    }
  }

  def ramsch(state: GameRoomActor.GameState): Receive = {
    case m => {
      println(s"ramsching $m")
    }
  }

  def whatAreWePlaying(state: GameRoomActor.GameState): Receive = {
    case m => {
      println(s"weArePlaying $m")
    }
  }
  /**
    * The game is being played.
    */
  def checkFailures(state: GameRoomActor.GameState): Receive = {
    case t: Messages.Game.Terminate => {
      println(f"Terminating the game ${room.name} due to ${t.reason}")

      state.players.foreach(
        _.session.ref ! t
      )

      self ! PoisonPill
    }

    case Terminated(ref: ActorRef) if playerActorMap.isDefinedAt(ref) => onPlayerLeft(ref)
    case Messages.Game.Leave() if playerActorMap.isDefinedAt(sender) => onPlayerLeft(sender)
  }

  // /**
  //  * The game is finished. Wait for rematch responses.
  //  */
  // def finished(pending: Set[ActorRef]): Receive = {
  //   case Messages.Player.Accept() if playerActorMap.isDefinedAt(sender) => {
  //     val newPending = pending - sender
  //     val player = playerActorMap(sender)
  //     println(f"Player ${player.player} has agreed to rematch")

  //     if (newPending.isEmpty) {
  //       println("Restarting game...")
  //       val sessions = playerActorMap.values

  //       sessions.foreach(player => player.ref ! Messages.Game.Restart)

  //       deal(sessions.head, sessions.tail.head, sessions.tail.tail.head)
  //     } else {
  //       println(f"Pending ${newPending.size} more replies")
  //       context.become(finished(newPending))
  //     }
  //   }

  //   case Messages.Player.Refuse() if playerActorMap.isDefinedAt(sender) => {
  //     val player = playerActorMap(sender)
  //     println(f"Player ${player.player} has refused to rematch")
  //     onPlayerLeft(sender)
  //   }
  // }

  /**
    * A player has left. Terminate the game.
    */
  def onPlayerLeft(ref: ActorRef): Unit = {
    val session = playerActorMap.get(ref).get
    println(f"Player ${session.player} has left the game.")
    self ! Messages.Game.Terminate(session.player)
  }
}
