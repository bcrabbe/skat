package com.bcrabbe.skat.common.domain

/**
 * Holds general information about a player.
 */
case class Player(id: String, name: String)

/**
 * Represents the state the player is currently in. Intended only for the player themselves to see.
 */
case class PlayerState(score: PlayerScore, biddingRole: BiddingRole, playing: Option[Boolean], hand: CardStack)

sealed trait BiddingRole
case object Geeben extends BiddingRole
case object Heuren extends BiddingRole
case object Zaagen extends BiddingRole

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
