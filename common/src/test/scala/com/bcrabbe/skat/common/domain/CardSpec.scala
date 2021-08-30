package com.bcrabbe.skat.common.domain;

import org.scalatest._
import org.scalatest.flatspec._
//import org.mockito.scalatest.IdiomaticMockito
import matchers.should._

class CardSpec extends AnyFlatSpec with Matchers {

  behavior of "CardStack"
  it should "deal 32 distinct cards" in {
    val deck: CardStack = CardStack.sorted
    val skat = deck.deal(2)
    val hands = List(deck.deal(10), deck.deal(10), deck.deal(10), skat)
    hands.foreach(println)
    hands.combinations(2).foreach(combs => combs(0).cards.intersect(combs(1).cards) should have length 0)
    deck.isEmpty shouldBe true
  }

}
