package com.bcrabbe.skat.common.domain

sealed trait Multiplyer {
  val value = 1
}
sealed trait LinearMultiplyer extends Multiplyer
sealed trait DoublingMultiplyer extends Multiplyer

case class Jacks(withOrWithout: Jacks.WithOrWithout, number: Int) extends LinearMultiplyer {
  override val value = number + 1 // i.e. "with 3, plays 4"
}

object Jacks {
  sealed trait WithOrWithout
  case object WithOrWithout extends WithOrWithout
  case object With extends WithOrWithout
  case object Without extends WithOrWithout
}

object Multiplyer {
  case class Hand() extends LinearMultiplyer
  case class Schnieder() extends LinearMultiplyer
  case class SchniederAngesagt() extends LinearMultiplyer
  case class Swartz() extends LinearMultiplyer
  case class SwartzAngesagt() extends LinearMultiplyer
  case class Ouvert() extends LinearMultiplyer
  case class Kontra() extends DoublingMultiplyer
  case class ReKontra() extends DoublingMultiplyer
  case class Schieberamsch(numberOfPasses: Int) extends DoublingMultiplyer {
    override val value = numberOfPasses + 1
  }
  case class Jungfrau(playersVirgin: Int) extends DoublingMultiplyer {
    override val value = playersVirgin
  }
}

case class GameMultiplyer(multiplyers: List[(Multiplyer, Boolean)]) {
  def linearMultiplyer = multiplyers.foldLeft(1)((acc, elem) => elem match {
    case (multiplyer: LinearMultiplyer, true) => acc + multiplyer.value
    case (_, _) => acc
  })
  def doublingMultiplyer = multiplyers.foldLeft(1)((acc, elem) => elem match {
    case (multiplyer: DoublingMultiplyer, true) => acc * 2 * multiplyer.value
    case (_, _) => acc
  })
}

object GameMultiplyer {
  def standardBiddedMultiplyers: GameMultiplyer = GameMultiplyer(List(
    (Multiplyer.Hand(), false),
    (Multiplyer.SchniederAngesagt(), false),
    (Multiplyer.SwartzAngesagt(), false),
    (Multiplyer.Ouvert(), false)
  ))
  def nullBiddedMultiplyers: GameMultiplyer = GameMultiplyer(List(
    (Multiplyer.Hand(), false),
    (Multiplyer.Ouvert(), false)
  ))
  def ramschMultiplyers: GameMultiplyer = GameMultiplyer(List(
    (Multiplyer.Schieberamsch(1), true),
    (Multiplyer.Jungfrau(1), true)
  ))
}
