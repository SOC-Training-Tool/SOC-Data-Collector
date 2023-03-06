package soc.datacollector.game

import soc.datacollector.GameId
import zio.IO

object GameRecorderService {

  trait Service {
    def startGame(request: StartRecordingGame): IO[Nothing, GameId]

    def recordMove(request: RecordMove): IO[Nothing, Unit]

    def completeGame(request: CompleteRecordingGame): IO[Nothing, Unit]
  }
}
