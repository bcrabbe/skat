package com.bcrabbe.skat.server

import com.bcrabbe.skat.server.lobby.LobbyActor
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe, TestActorRef }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike
import scala.concurrent.duration._
import com.bcrabbe.skat.common.domain.{Player, GameRoom}
import com.bcrabbe.skat.common.Messages
import com.bcrabbe.skat.server.game.GameRoomActor

class LobbyActorTest() extends TestKit(ActorSystem("LobbyActorTest"))
    with AnyFlatSpecLike
    with ImplicitSender
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  behavior of "Lobby"

  it should "create game" in {
    val roomProbe = TestProbe("GameRoom")
    val lobby = TestActorRef(new LobbyActor {
      override def startRoom(room: GameRoom) = roomProbe.ref
    }, "lobby")

    val players: List[TestProbe] = List.range(1, 4).map(n => new TestProbe(system) {
      val player = Player(this.ref.path.name, s"PlayerProbe$n")
      lobby ! Messages.Server.Connect(player)
    })

    roomProbe.expectMsgType[GameRoomActor.Messages.ReceivePlayers]
  }

}
