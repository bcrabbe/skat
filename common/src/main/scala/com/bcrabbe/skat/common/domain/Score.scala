package com.bcrabbe.skat.common.domain

/**
 * Represents the score a player has in a game.
 */
case class PlayerScore(totalPoints: Int)

object PlayerScore {
  val zero = PlayerScore(0)
}
