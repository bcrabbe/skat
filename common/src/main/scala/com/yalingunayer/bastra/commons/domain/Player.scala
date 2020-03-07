package com.yalingunayer.bastra.commons.domain

/**
 * Holds general information about a player.
 */
case class Player(id: String, name: String)

sealed trait BiddingRole
case class Geeben extends BiddingRole
case class Heuren extends BiddingRole
case class Zaagen extends BiddingRole

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

/**
 * Represents the state the player is currently in. Intended only for the player themselves to see.
 */
case class PlayerState(session: PlayerSession, score: PlayerScore, biddingRole: BiddingRole, playing: Option[Bool], hand: CardStack)
