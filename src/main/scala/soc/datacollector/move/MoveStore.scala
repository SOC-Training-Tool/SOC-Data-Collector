package soc.datacollector.move

import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, GameId, MOVE}
import zio.clock.Clock
import zio.{IO, RIO, ZIO}

object MoveStore {

  trait Service {

    def startRecording(platform: String, playerIds: Seq[String], board: BOARD): IO[Throwable, GameId]

    def recordMove(gameId: GameId, move: MOVE): IO[Throwable, Unit] = ZIO.unit

    def completeRecording(gameId: GameId)

  }


}
