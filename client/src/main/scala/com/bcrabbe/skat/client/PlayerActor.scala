package com.bcrabbe.skat.client

import akka.actor.Actor
import com.bcrabbe.skat.common.domain.{ Scores, Card, CardStack, GameRoom, Player }
import com.bcrabbe.skat.common.Utils
import akka.actor.PoisonPill
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

import scala.concurrent.{ Future }
import akka.actor.ActorRef
import scala.util.{ Failure, Success }
import akka.actor.Props
import com.bcrabbe.skat.common.Messages

object PlayerActor {
  def props: Props = Props(classOf[PlayerActor])
}

class PlayerActor extends Actor {
  private object Events {
    case class ServerFound(ref: ActorRef)
    case class CardPlayed(card: Card)
  }

  /**
   * The currently joined server
   */
  var server: ActorRef = null

  /**
   * The currently joined game room
   */
  var room: GameRoom = null

  /**
   * Actor reference to the currently joined game room
   */
  var roomRef: ActorRef = null

  /**
   * The player
   */
  var me: Player = null

  /**
   * The opponent
   */
  var opponents: List[Player] = null

  /**
   * The player's hand
   */
  var hand: CardStack = null

  /**
   * The player's hand
   */
  var bidded: Int = 0

  /**
   * Reset the game state. This is usually called before restarting a new game.
   */
  def resetState: Unit = {
    room = null
    opponents = null
    hand = null
    bidded = 0
  }

  /**
   * Leave the room and reset the game state
   */
  def leaveRoom: Unit = {
    roomRef ! Messages.Game.Leave()
    resetState
    room = null
    roomRef = null
    context.become(waiting)
  }

  /**
   * The player is waiting to connect to a game lobby
   */
  def connecting: Receive = {
    case m: Messages.Server.Connected => {
      println(f"Connected to the server, waiting for opponents...")
      context.become(waiting)
    }
  }

  /**
   * The player is now in the lobby, and waiting to join a game room
   */
  def waiting: Receive = {
    case m: Messages.Game.Joined => {
      room = m.room
      roomRef = sender

      println(f"You have joined a game room: ${room.name}")
      context.become(waitingToStart)
    }
  }

  /**
   * The player has joined a game room and is now waiting for the game to start
   */
  def waitingToStart: Receive = playing orElse {
    case m: Messages.Game.SetUp => {
      opponents = m.opponents

      println(f"You are playing with ${opponents.map(_.name).mkString(" and ")}")
      context.become(waitingForHand)
    }
  }

  def waitingForHand: Receive = playing orElse {
    case Messages.Game.CardsDelt(cards: CardStack) => {
      hand = cards
      println(s"You were delt $cards")
      context.become(waitingForBiddingRole)
    }
  }

  def waitingForBiddingRole: Receive = playing orElse {
    case Messages.Game.Bidding.Roles.Speaking(player: Player) => {
      println(s"you are speaking to ${player.name}")
      makeBid.map(context.become(_))
    }
    case Messages.Game.Bidding.Roles.Waiting(player: Player) => {
      println(s"you are waiting for ${player.name}")
      context.become(watchingBidding)
    }
    case Messages.Game.Bidding.Roles.Listening(player: Player) => {
      println(s"you are listening to ${player.name}")
      context.become(replyBidding)
    }
    case Messages.Game.Bidding.Roles.Passed() => {
      println(s"you are waiting for bidding to finish")
      context.become(watchingBidding)
    }
  }

  def makeBid: Future[Receive] = {
    val (nextBidValue, nextBidGames) = Scores.nextScore(bidded)
    println(s"Would you like to bid $nextBidValue? ($nextBidGames)")
    def doPrompt: Future[Receive] = Utils.readBooleanResponse map {
      case true => {
        roomRef ! Messages.Game.Bidding.Bid(nextBidValue)
        bidded = nextBidValue
        println(s"you offered $nextBidValue")
        awaitingBidResponse
      }
      case false => {
        roomRef ! Messages.Game.Bidding.Pass()
        println(s"you passed on $nextBidValue")
        waitingForBiddingRole orElse whatAreWePlaying
      }
    } recoverWith {
      case _ => {
        println("Invalid input, please try again.")
        doPrompt
      }
    }
    doPrompt
  }

  def awaitingBidResponse: Receive = playing orElse {
    case Messages.Game.Bidding.ObservedAccept(player: Player, bid: Int) => {
      println(s"${player.name} accepted $bid")
      makeBid.map(context.become(_))
    }
    case Messages.Game.Bidding.ObservedPass(player: Player, bid: Int) => {
      println(s"${player.name} passed $bid")
      context.become(waitingForBiddingRole orElse whatAreWePlaying)
    }
    case m => {
      println(s"ignoring $m from $sender - awaitingBidResponse")
    }
  }

  def watchingBidding: Receive = waitingForBiddingRole orElse {
    case Messages.Game.Bidding.ObservedBid(player: Player, bid: Int) => {
      println(s"${player.name} bid $bid")
    }
    case Messages.Game.Bidding.ObservedAccept(player: Player, bid: Int) => {
      println(s"${player.name} accepted $bid")
    }
    case Messages.Game.Bidding.ObservedPass(player: Player, bid: Int) => {
      println(s"${player.name} passed $bid")
      context.become(waitingForBiddingRole orElse whatAreWePlaying)
    }
    case m => {
      println(s"ignoring $m from $sender - watchingBidding")
    }
  }

  def replyBidding: Receive = playing orElse {
    case Messages.Game.Bidding.ObservedBid(player: Player, bid: Int) => {
      def doPrompt: Future[Receive] = {
        println(s"${player.name} bid $bid. Match?")
        Utils.readBooleanResponse map {
          case true => {
            roomRef ! Messages.Game.Bidding.AcceptBid()
            println(s"you accepted $bid")
            replyBidding
          }
          case false => {
            roomRef ! Messages.Game.Bidding.Pass()
            println(s"you passed on $bid")
            waitingForBiddingRole orElse whatAreWePlaying
          }
        } recoverWith {
          case _ => {
            println("Invalid input, please try again.")
            doPrompt
          }
        }
      }
      doPrompt
    }
  }

  def whatAreWePlaying: Receive = playing

  /**
   * An abstract state that covers the part where the player is playing the game, whether it's their turn or their opponent's
   */
  def playing: Receive = {
    case t: Messages.Game.Terminate => t.reason match {
      case reason: Messages.Game.Terminate.Reason.PlayerLeft if reason.player != me => {
        println(f"Your opponent has left the game, you will be returned to the lobby")
        leaveRoom
      }
      case reason: Messages.Game.Terminate.Reason.ErrorOccurred => {
        println(f"The game was terminated due to an error, you will be returned to the lobby. Reason was: ${reason.error}")
        leaveRoom
      }
    }
    case Messages.Server.Message(msg) => println(f"[Server]: $msg")
  }

  // /**
  //  * It's the opponent's turn
  //  */
  // def opponentInTurn: Receive = turnPending

  // /**
  //  * It's the player's turn
  //  */
  // def inTurn: Receive = turnPending orElse {
  //   case Events.CardPlayed(card) => {
  //     roomRef ! Messages.Game.CardPlayed(card)

  //     println(f"You have played $card")
  //   }
  // }

  /**
   * The game is finished. Wait for a possible restart
   */
  def finished: Receive = playing orElse {
    case Messages.Game.Restart => {
      println("Opponent accepted rematch, starting a new round")
      resetState
      context.become(waitingToStart)
    }
  }

  /**
   * The default state is the connecting state
   */
  def receive = connecting

  /**
   * Try reconnecting until successful
   */
  def tryReconnect = {
    def doTry(attempts: Int): Unit = {
      context.system.actorSelection("akka://SkatServer@127.0.0.1:47000/user/lobby").resolveOne()(10.seconds).onComplete(x => x match {
        case Success(ref: ActorRef) => {
          println(s"Server found (ref=$ref), attempting to connect...")
          server = ref
          server ! Messages.Server.Connect(me)
        }
        case Failure(t) => {
          System.err.println(f"No game server found, retrying (${attempts + 1})...")
          Thread.sleep(5000) // this is almost always a bad idea, but let's keep it for simplicity's sake
          doTry(attempts + 1)
        }
      })
    }

    println("Attempting to find a game server...")
    context.become(connecting)
    doTry(0)
  }

  override def preStart(): Unit = {
    println("Welcome to Skat! Please enter your name.")
    Utils.readResponse.onComplete {
      case Success(name: String) => {
        me = Player(Utils.uuid(), name)
        tryReconnect
      }

      case _ => self ! PoisonPill
    }
  }
}
