package com.bcrabbe.skat.server

import com.bcrabbe.skat.common.domain.GameRoom
import akka.actor.Props
import akka.actor.Actor
import com.bcrabbe.skat.common.Messages
import com.bcrabbe.skat.common.domain.CardStack
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.remote.DisassociatedEvent
import akka.actor.Terminated
import scala.util.Random
import com.bcrabbe.skat.common.domain.Card
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.bcrabbe.skat.common.domain.PlayerScore
import com.bcrabbe.skat.common.domain.PlayerState

object GameRoomActor {

  case class GameState(skat: CardStack, players: List[PlayerState])

  case class StateResult(newState: GameRoomActor.GameState, winner: Option[PlayerState], isFished: Boolean)

  case class PlayerInfo(session: PlayerSession, state: PlayerState)

  object Messages {
    case class ReceivePlayers(player: List[PlayerSession])
  }

  def props(room: GameRoom) = Props(classOf[GameRoomActor], room)

  /**
   * Determine the next state based on a card that was played
   */
  def determineNextState(card: Card, playerInfo: PlayerInfo, opponentInfo: PlayerInfo, deckCards: List[Card], middleCards: List[Card]): StateResult = {
    val player = playerInfo.state
    val opponent = opponentInfo.state

    // determine if the player has fished a card, what their scores will be, and what their bucket will contain on the next turn
    // since this will also affect the middle stack, determine its new state as well
    val (isFished, newScore, newMiddleStack, newBucketStack) = middleCards.headOption match {
      case Some(other) if card.canFish(other) => {
        val newBucketCards = card :: middleCards ++ player.bucket.cards

        // find out if a skat is performed
        val isSkat = middleCards match {
          case x :: Nil if (x == card) => true
          case _ => false
        }

        val earnedPoints = (card :: middleCards).map(_.score).sum + (if (isSkat) 10 else 0)

        val newScore = PlayerScore(earnedPoints + player.score.totalPoints, player.score.skats + (if (isSkat) 1 else 0), newBucketCards.length)

        (true, newScore, CardStack.empty, CardStack(newBucketCards))
      }
      case _ => (false, player.score, CardStack(card :: middleCards), player.bucket)
    }

    val proposedNextHand = player.hand.removed(card).cards
    val bothHandsEmpty = proposedNextHand.isEmpty && opponent.hand.isEmpty
    val isFinished = bothHandsEmpty && deckCards.isEmpty
    val shouldDeal = !isFinished && bothHandsEmpty

    // determine if we need to deal new cards to players
    val (nextDeck, nextPlayerHand, nextOpponentHand) = shouldDeal match {
      case true => (deckCards.drop(8), deckCards.take(4), deckCards.drop(4).take(4))
      case _ => (deckCards, proposedNextHand, opponent.hand.cards)
    }

    val nextPlayerState = PlayerState(CardStack(nextPlayerHand), newBucketStack, newScore)
    val nextPlayerInfo = PlayerState(playerInfo.session, nextPlayerState)

    val nextOpponentState = PlayerState(CardStack(nextOpponentHand), opponent.bucket, opponent.score)
    val nextOpponentInfo = PlayerState(opponentInfo.session, nextOpponentState)

    val nextState = GameState(isFinished, CardStack(nextDeck), newMiddleStack, nextOpponentInfo, nextPlayerInfo)

    // determine the winner (if any)
    val winner: Option[PlayerState] = isFinished match {
      case true => (nextPlayerInfo :: nextOpponentInfo :: Nil).sortBy(_.state.score.totalPoints).tail.headOption
      case _ => None
    }

    StateResult(nextState, winner, isFished)
  }
}

class GameRoomActor(val room: GameRoom) extends Actor {
  var players: Map[ActorRef, PlayerSession] = Map()

  /**
   * Determine the next state of the game given its current state and a card that was played
   */
  def getNextState(player: ActorRef, card: Card, current: GameRoomActor.GameState): Try[GameRoomActor.StateResult] = {
    if (current.playerInTurn.session.ref != player) {
      Failure(new Exception("Please wait until it's your turn."))
    } else if (!current.playerInTurn.state.hand.cards.contains(card)) {
      Failure(new Exception("You have played an invalid card."))
    } else {
      val player = current.playerInTurn
      val opponent = current.playerWaiting
      val deckCards = current.deck.cards
      val middleCards = current.middleStack.cards

      val result = GameRoomActor.determineNextState(card, player, opponent, deckCards, middleCards)

      Success(result)
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

  /**
   * The inital state. Wait for the matchmaking server to introduce the players.
   */
  def initializing: Receive = {
    case GameRoomActor.Messages.ReceivePlayers(playersForGame: List[PlayerSession]) => {
      //store player refs
      val initAcc: Map[ActorRef, PlayerSession] = Map()
      players = playersForGame.foldLeft(initAcc)(
        (acc, player) => {
          context.watch(player.ref)
          acc + (player.ref -> player)
        }
      )
      //notify players of opponents
      playersForGame.foreach((playerSession) => {
        playerSession.ref ! Messages.Game.Joined(room)
        val opponents: List[Player] = players.filter((p) => p != playerSession).map(_.player)
        playerSession.ref ! Messages.Game.SetUp(opponents)
      })
      val initPlayerStates: List[PlayerState] = playersForGame.zip(BiddingRole.all).map(
        ((playerSession: PlayerSession, biddingRole: BiddingRole)) =>
        PlayerState(playerSession, PlayerScore(0), biddingRole, Option.empty, CardStack.empty)
      )
      deal(initPlayerStates)
    }
  }

  /**
   * Start a new hand.
   */
  def deal(playerStates: List[PlayerState]) = {
    val deck = CardStack.shuffled.cards
    val skat: List[CardStack] = deck.deal(2)
    val hands: List[CardStack] = List(deck.deal(10), deck.deal(10), deck.deal(10))
    val playersWithHands: List[PlayerState] = playerStates.zip(hands).map(((playerState, hand)) => playerState.copy(hand = hand))
    playersWithHands.foreach((playerState) => playerState.session.ref ! Messages.Game.CardsDelt(playerState.hand))
    val initialState = GameRoomActor.GameState(BiddingPhase, skat, playersWithHands)
    context.become(bidding(initialState))
    println(s"Starting a game between $playersWithHands")
    //send initial bidding message
    val zaagenPlayer = state.players.find((playerState) => playerState.biddingRole == Zaagen)
    zaagenPlayer.ref ! Messages.Game.Bidding.Speaking(points = 18)
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

    case Messages.Game.CardPlayed(card) if players.isDefinedAt(sender) => {
      println(f"Player ${sender} played ${card}")
      getNextState(sender, card, state) match {
        case Success(GameRoomActor.StateResult(state, winner, isFished)) => {
          winner match {
            case Some(player) => {
              val opponent = players.values.filter(_.ref != player.session.ref).head
              println(f"Game finished, winner is ${player}")

              player.session.ref ! Messages.Game.Win()
              opponent.ref ! Messages.Game.Lose()

              context.become(finished(players.keys.toSet))
            }
            case _ => {
              val nextPlayer = state.playerInTurn
              val previousPlayer = state.playerWaiting

              println(f"Card accepted, moving to new turn. Player in turn: ${nextPlayer.state}, opponent: ${previousPlayer.state}, game state: ${state}")

              nextPlayer.session.ref ! Messages.Game.InTurn(state.middleStack.cards.headOption, isFished, nextPlayer.state, previousPlayer.state.score)
              previousPlayer.session.ref ! Messages.Game.OpponentInTurn(state.middleStack.cards.headOption, isFished, previousPlayer.state, nextPlayer.state.score)

              context.become(playing(state))
            }
          }
        }
        case Failure(e) => sender ! Messages.Server.Message(e.getMessage())
      }
    }
  }

  /**
   * The game is finished. Wait for rematch responses.
   */
  def finished(pending: Set[ActorRef]): Receive = {
    case Messages.Player.Accept() if players.isDefinedAt(sender) => {
      val newPending = pending - sender
      val player = players(sender)
      println(f"Player ${player.player} has agreed to rematch")

      if (newPending.isEmpty) {
        println("Restarting game...")
        val sessions = players.values

        sessions.foreach(player => player.ref ! Messages.Game.Restart)

        deal(sessions.head, sessions.tail.head, sessions.tail.tail.head)
      } else {
        println(f"Pending ${newPending.size} more replies")
        context.become(finished(newPending))
      }
    }

    case Messages.Player.Refuse() if players.isDefinedAt(sender) => {
      val player = players(sender)
      println(f"Player ${player.player} has refused to rematch")
      onPlayerLeft(sender)
    }
  }

  def receive = initializing
}
