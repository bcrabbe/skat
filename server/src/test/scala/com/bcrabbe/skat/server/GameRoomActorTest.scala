package com.bcrabbe.skat.server.game

import com.bcrabbe.skat.server.lobby.LobbyActor
import akka.actor.{ ActorSystem, Terminated }
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe, TestActorRef }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import scala.concurrent.duration._
import com.bcrabbe.skat.common.domain.{Player, GameRoom}
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
    val players: List[TestProbe] = List.range(1, 4).map(n => new TestProbe(system) {
      val player = Player(this.ref.path.name, s"PlayerProbe$n")
      val playerSession = PlayerSession(player, self)
    })
    val room = new GameRoom(name = s"DealTestRoom")
    val gameRoom = TestActorRef(new GameRoomActor(room), "room")

    gameRoom ! GameRoomActor.Messages.ReceivePlayers(
      players.map(probe => PlayerSession(
        Player(probe.ref.path.name, "playerProbe"),
        probe.ref
      ))
    )

    players(0).expectMsg(Messages.Game.Joined(room))

    gameRoom ! PoisonPill
  }

//   it should "know when a player leaves" in {
//     val roomProbe = TestProbe("GameRoom")
//     val lobby = TestActorRef(new LobbyActor {
//       override def startRoom(room: GameRoom) = roomProbe.ref
//     }, "lobby")

//     val player1 = new TestProbe(system) {
//       val player = Player(this.ref.path.name, s"PlayerProbe-quiter")
//       lobby ! Messages.Server.Connect(player)
//     }
//     val player2 = new TestProbe(system) {
//       val player = Player(this.ref.path.name, s"PlayerProbe-stays")
//       lobby ! Messages.Server.Connect(player)
//     }
// //    player1.stop()
//     lobby ! Terminated(player1.ref)

//     within(1 seconds, 2 seconds) {
//       lobby.underlyingActor.waiting.length shouldEqual 1
//     }

//   }

}
