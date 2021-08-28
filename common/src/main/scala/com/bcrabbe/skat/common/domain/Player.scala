package com.bcrabbe.skat.common.domain

import  com.bcrabbe.skat.common.Messages

/**
 * Holds general information about a player.
 */
case class Player(id: String, name: String)

/**
 * Represents the state the player is currently in. Intended only for the player themselves to see.
 */
case class PlayerState(
  score: PlayerScore,
  biddingRole: BiddingRole,
  playing: Option[Boolean],
  hand: CardStack
)

sealed trait BiddingRole {
  def message: Messages.Game.Bidding.RoleMessage
}
case object Geeben extends BiddingRole {
  def message = Messages.Game.Bidding.Roles.Waiting()
}
case object Heuren extends BiddingRole {
  def message = Messages.Game.Bidding.Roles.Listening()
}
case object Zaagen extends BiddingRole {
  def message = Messages.Game.Bidding.Roles.Speaking()
}

object BiddingRole {
  def all: List[BiddingRole] = List(Geeben, Heuren, Zaagen)
}

/**
 * Represents the score a player has in a game.
 */
case class PlayerScore(totalPoints: Int)

object PlayerScore {
  val zero = PlayerScore(0)
}
