package com.bcrabbe.skat.server

import com.bcrabbe.skat.common.domain.GameRoom
import com.bcrabbe.skat.common.domain.Player
import akka.actor.ActorRef

/**
 * A class that represents a player's session
 */
case class PlayerSession(val player: Player, val ref: ActorRef)

/**
 * A class that represents a game session
 */
case class GameSession(val room: GameRoom, val ref: ActorRef, val players: List[PlayerSession])
