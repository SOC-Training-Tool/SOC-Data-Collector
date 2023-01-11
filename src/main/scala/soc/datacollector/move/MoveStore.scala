package soc.datacollector.move

import soc.datacollector.domain.{BOARD, GameId, MOVE}
import zio.clock.Clock
import zio.RIO

object MoveStore {

  trait Service {

    def addMove(gameId: GameId, move: MOVE): RIO[Clock, Unit]

  }


}
