package com.bcrabbe.skat.common.domain

object Scores {
  val allGames: Seq[Game] = List(
    Colour(Suit.Diamonds),
    Colour(Suit.Hearts),
    Colour(Suit.Spades),
    Colour(Suit.Clubs),
    Grand(),
    Null()
  )
  def main(args: Array[String]): Unit = {
    println(calculateScore(allGames))
  }

  def calculateScores(games: Seq[Game]): List[Int] = {
    val scoreList: Map[(Int, Set[Game])] = games.foldLeft(HashMap.empty[(Int, Set.empty[Game])])(
      (scores, game) => {
        val possibleScores: List[(Int, Game)] =
      }
    )
  }

  def allScoresForGame(game: Game): List[(Int, Game)] = game match {
    case biddedGame: BiddedGame => biddedGame match {

    }
  }

  def allScoreForJacksGame(game: JacksMultiply): List[(Int, Game)] = {
    List.range(1, 5).map(game.copy(jacks = Multiplyer.Jacks(Multiplyer.Jacks.With, number)))
  }

}

sealed trait Game

sealed trait BiddedGame {
  val baseValue: Int
  val biddedMultiplyers: GameMultiplyer
  def bidValue: Int = baseValue * biddedMultiplyers.value
  def finalValue: Int = bidValue
}
sealed trait JacksMultiply {
  val jacks: Multiplyer.Jacks
}

case class Colour(suit: Suit, jacks: Mulitplyer.Jacks) extends Game with BiddedGame with JacksMultiply {
  val baseValue: Int = suit match {
    case Diamonds() => 9
    case Hearts() => 10
    case Spades() => 11
    case Clubs() => 12
  }
  val biddedMultiplyers: GameMultiplyer = GameMultiplyer.standardBiddedMultiplyers
}
case class Grand(jacks: Mulitplyer.Jacks) extends Game with BiddedGame with JacksMultiply {
  val baseValue = 24
  val biddedMultiplyers: GameMultiplyer = GameMultiplyer.standardBiddedMultiplyers
}
case class Null() extends Game with BiddedGame {
  baseValue = 23
  val biddedMultiplyers: GameMultiplyer = GameMultiplyer.nullMultiplyers
}
case class Ramsch() extends Game

abstact class Multiplyer(value = 1: Int) {
  def op((Int, Int)): (Int, Int)
}

object Multiplyer {
  class Hand extends Multiplyer
  class Schnieder extends Multiplyer
  class SchniederAngesagt extends Multiplyer
  class Swartz extends Multiplyer
  class SwartzAngesagt extends Multiplyer
  class Ouvert extends Multiplyer
  class Jacks(withOrWithout: Jacks.WithOrWithout, number: Int) extends Multiplyer {
    value = number + 1 // i.e. "with 3, plays 4"
  }
  object Jacks {
    sealed trait WithOrWithout
    class With extends WithOrWithout
    class Without extends WithOrWithout
  }
  class Kontra extends Multiplyer
  class ReKontra extends Multiplyer
  class Schieberamsch(numberOfPasses: Int) {
    value = numberOfPasses + 1
  }
  class Jungfrau(playersVirgin: Int) {
    value = playersVirgin
  }
}

case class GameMultiplyer(multiplyers: List[(multiplyer: Multiplyer, applied: Boolean)]) {
  def linearMultiplyer = multiplyers.foldLeft(1)((acc, multiplyer) => multiplyer.applied match {
    case True => acc + multiplyer.value
    case False => acc
  })
}

object GameMultiplyer {
  def standardBiddedMultiplyers: GameMultiplyer = GameMultiplyer(List(
    (Multiplyer.Hand, false),
    (Multiplyer.SchniederAngesagt, false),
    (Multiplyer.SwartzAngesagt, false),
    (Multiplyer.Ouvert, false)
  ))
  def nullBiddedMultiplyers: GameMultiplyer = List(
    (Multiplyer.Hand, false),
    (Multiplyer.Ouvert, false)
  )
  def ramschMultiplyers: GameMultiplyer = List(
    (Multiplyer.Schieberamsch(1), true),
    (Multiplyer.Jungfrau(1), true),
  )
}
