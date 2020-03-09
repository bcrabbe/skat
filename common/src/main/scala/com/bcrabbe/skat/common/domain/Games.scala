package com.bcrabbe.skat.common.domain

sealed trait Game

case class Colour(suit: Suit, jacks: Jacks) extends Game with BiddedGame with JacksMultiply {
  val baseValue: Int = suit match {
    case Diamonds => 9
    case Hearts => 10
    case Spades => 11
    case Clubs => 12
  }
  val biddedMultiplyers: GameMultiplyer = GameMultiplyer.standardBiddedMultiplyers
}

case class Grand(jacks: Jacks) extends Game with BiddedGame with JacksMultiply {
  val baseValue = 24
  val biddedMultiplyers: GameMultiplyer = GameMultiplyer.standardBiddedMultiplyers
}

case class Null() extends Game with BiddedGame {
  val baseValue = 23
  val biddedMultiplyers: GameMultiplyer = GameMultiplyer.nullBiddedMultiplyers
}

case class Ramsch() extends Game

sealed trait BiddedGame {
  val baseValue: Int
  val biddedMultiplyers: GameMultiplyer
}

sealed trait JacksMultiply {
  val jacks: Jacks
}

object Scores {
  val biddingScores: Seq[(Int, Seq[Game])] = List(
    (18, List(Colour(Diamonds, Jacks(Jacks.WithOrWithout, 1)))),
    (20, List(Colour(Hearts, Jacks(Jacks.WithOrWithout, 1)))),
    (22, List(Colour(Spades, Jacks(Jacks.WithOrWithout, 1)))),
    (23, List(Null())),
    (24, List(Colour(Clubs, Jacks(Jacks.WithOrWithout, 1)))),
    (27, List(Colour(Diamonds, Jacks(Jacks.WithOrWithout, 2)))),
    (30, List(Colour(Hearts, Jacks(Jacks.WithOrWithout, 2)))),
    (33, List(Colour(Spades, Jacks(Jacks.WithOrWithout, 2)))),
    (35, List(Null())),
    (36, List(Colour(Clubs, Jacks(Jacks.WithOrWithout, 2)))),
    (48, List(Grand(Jacks(Jacks.WithOrWithout, 1))))
  )
}
