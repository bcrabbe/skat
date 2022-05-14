package com.bcrabbe.skat.server.game

import com.bcrabbe.skat.server.lobby.LobbyActor
import akka.actor.{ ActorSystem, Terminated }
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe, TestActorRef }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import scala.concurrent.duration._
import com.bcrabbe.skat.common.domain.{Player, GameRoom, CardStack, Geeben, Heuren, Zaagen, BiddingRole}
import com.bcrabbe.skat.common.Messages
import com.bcrabbe.skat.common.Messages.Game.Bidding.Roles.{ Listening, Speaking, Waiting, Passed }
import com.bcrabbe.skat.common.Messages.Game.Bidding.RoleMessage
import akka.actor.{ PoisonPill, ActorRef }
import com.bcrabbe.skat.server.session.PlayerSession
import scala.concurrent.duration.Duration


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
    val roles: Map[RoleMessage, List[akka.testkit.TestProbe]] = players.groupBy(_.expectMsgType[RoleMessage])
    // should give one of each starting role
    roles.keySet.size shouldBe 3
    // should not give same role to two players
    roles.values.toSet.size shouldBe 3

    gameRoom ! PoisonPill
  }

  it should "initial bidding roles" in {
    case class PlayerProbeInfo(name: String, player: Player, probe: TestProbe)
    val probes: List[PlayerProbeInfo] = List.range(1, 4).map(n => {
      PlayerProbeInfo(
        name = s"probe$n",
        player = Player(s"$n", s"probe$n"),
        probe = TestProbe(s"probe$n")
      )
    })
    val room = new GameRoom(name = s"BiddingTestRoom")
    val gameRoom = TestActorRef(new GameRoomActor(room), "room")
    gameRoom ! GameRoomActor.Messages.ReceivePlayers(
      probes.map(probe => PlayerSession(probe.player, probe.probe.ref))
    )
    val playerByRole: Map[BiddingRole, List[akka.testkit.TestProbe]] = probes.map(_.probe).groupBy(_.fishForSpecificMessage(1.second, "wait for role message") {
      case _: Speaking => Zaagen
      case _: Listening => Heuren
      case _: Waiting => Geeben
    })
    println(playerByRole)
    playerByRole.keys.to[Set].intersect(Set(Geeben, Heuren, Zaagen)) should have size 3
    playerByRole.values.map(_.head).to[Set].intersect(probes.map(_.probe).to[Set]) should have size 3
    gameRoom ! PoisonPill
  }
}
