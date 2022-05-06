package com.bcrabbe.skat.server

import com.bcrabbe.skat.server.lobby.LobbyActor
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyFlatSpec
import scala.concurrent.duration._

import com.bcrabbe.skat.common.Messages

class SkatServerIT extends TestKit(ActorSystem("SkatServerIT"))
    with AnyFlatSpec
    with ImplicitSender
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  behavior of "Lobby"

  it should "deal 32 distinct cards" in {
    val lobby = system.actorOf(LobbyActor.props(), "lobby")
    val players: List[TestProbe] = (TestProbe("player1"), TestProbe("player2"), TestProbe("player3"))
    players.foreach(player => lobby ! Messages.Server.Connect(Player(player.ref.path.name, player.ref.path.name)))

  }

}
