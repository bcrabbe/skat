package com.bcrabbe.skat.common.domain

import java.util.Date
import scala.concurrent.duration.Duration

/**
 * A class that represents a game room.
 * This doesn't hold much information, but it's useful to abstract it away.
 */
case class GameRoom(val name: String) {
  val createdAt = new Date
  val turnTime = Duration.Inf
}
