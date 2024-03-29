package com.bcrabbe.skat.common

import com.bcrabbe.skat.common.domain._

object Messages {
  object Server {
    case class Connect(player: Player)
    case class Connected()
    case class Message(message: String)
  }

  object Game {
    case class Joined(room: GameRoom)
    case class SetUp(opponents: List[Player])
    case class CardsDelt(cards: CardStack)

    case class CardPlayed(card: Card)
    case class Win()
    case class Lose()
    case class InTurn(top: Option[Card], isFished: Boolean, state: PlayerState, opponentScore: PlayerScore)
    case class OpponentInTurn(top: Option[Card], isFished: Boolean, state: PlayerState, opponentScore: PlayerScore)
    case class Restart()
    case class Leave()

    object Bidding {
      trait RoleMessage
      object Roles {
        case class Listening(to: Player) extends RoleMessage
        case class Speaking(to: Player) extends RoleMessage
        case class Waiting(to: Player) extends RoleMessage
        case class Passed() extends RoleMessage
      }
      case class Bid(points: Int)
      case class AcceptBid()
      case class Pass()
      case class ObservedBid(player: Player, points: Int)
      case class ObservedAccept(player: Player, points: Int)
      case class ObservedPass(player: Player, points: Int)
    }

    object Terminate {
      trait Reason

      object Reason {
        case class PlayerLeft(player: Player)
        case class ErrorOccurred(error: Throwable)
      }

      def apply(p: Player): Reason.PlayerLeft = Reason.PlayerLeft(p)
      def apply(t: Throwable): Reason.ErrorOccurred = Reason.ErrorOccurred(t)
    }

    case class Terminate(reason: Terminate.Reason)
  }
}
