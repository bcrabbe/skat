package com.bcrabbe.skat.server.game

import com.bcrabbe.skat.server.lobby.LobbyActor
import akka.actor.{ ActorSystem, Terminated }
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe, TestActorRef }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import scala.concurrent.duration._
import com.bcrabbe.skat.common.domain.{Player, GameRoom, CardStack}
import com.bcrabbe.skat.common.Messages
import akka.actor.PoisonPill
import com.bcrabbe.skat.server.session.PlayerSession

class GameRoomActorTest() extends TestKit(ActorSystem("GameRoomActorTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  behavior of "GameRoom"

  it should "deal" in {
    val players: List[TestProbe] = List.range(1, 4).map(n => TestProbe())
    val room = new GameRoom(name = s"DealTestRoom")
    val gameRoom = TestActorRef(new GameRoomActor(room), "room")

    gameRoom ! GameRoomActor.Messages.ReceivePlayers(
      players.map(probe => PlayerSession(
        Player(probe.ref.path.name, "playerProbe"),
        probe.ref
      ))
    )
    players.foreach(_.expectMsg(Messages.Game.Joined(room)))
    players.foreach(_.expectMsgType[Messages.Game.SetUp])
    val hands: List[CardStack] = players.map(_.expectMsgType[Messages.Game.CardsDelt].cards)
    // should deal 10 cards
    hands.foreach(_.cards.length shouldBe 10)
    // should not deal same card twice
    hands.combinations(2).foreach(combs => combs(0).cards.intersect(combs(1).cards) should have length 0)
    val roles: List[Messages.Game.Bidding.RoleMessage] = players.map(_.expectMsgType[Messages.Game.Bidding.RoleMessage])
    // should not give same role to two players
    roles.distinct.length shouldBe 3
    gameRoom ! PoisonPill
  }

  it should "bidding" in {
    val players: List[TestProbe] = List.range(1, 4).map(n => TestProbe())
    val room = new GameRoom(name = s"BiddingTestRoom")
    val gameRoom = TestActorRef(new GameRoomActor(room), "room")

    gameRoom ! GameRoomActor.Messages.ReceivePlayers(
      players.map(probe => PlayerSession(
        Player(probe.ref.path.name, "playerProbe"),
        probe.ref
      ))
    )

    val playerByRole: Map[Messages.Game.Bidding.RoleMessage, ActorRef] = players.groupBy(_.expectMsgType[Messages.Game.Bidding.RoleMessage])

    playerByRole(Geeben.message)
    gameRoom ! PoisonPill
  }
}
