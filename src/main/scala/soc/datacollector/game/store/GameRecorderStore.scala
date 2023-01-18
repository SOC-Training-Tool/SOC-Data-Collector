package soc.datacollector.game.store


import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE, PlayerId}
import soc.datacollector.game.{CompleteRecordingGameResult, StartGameData}
import zio.{Has, Tag, ULayer, ZIO, ZLayer}

object GameRecorderStore {

  type GameRecorderStore[R] = Has[Service[R]]

  def startRecording[R: Tag](platform: String, playerIds: Seq[String], board: BOARD): ZIO[R with GameRecorderStore[R], Throwable, GameId] = {
    ZIO.service[Service[R]].flatMap(_.startRecording(platform, playerIds, board))
  }

  def recordMove[R: Tag](gameId: GameId, previousMoveNumber: Int, move: MOVE): ZIO[R with GameRecorderStore[R], Throwable, Int] = {
    ZIO.service[Service[R]].flatMap(_.recordMove(gameId, previousMoveNumber, move))
  }

  def completeRecording[R: Tag](gameId: GameId, totalMoveCount: Int): ZIO[R with GameRecorderStore[R], Throwable, CompleteRecordingGameResult] = {
    ZIO.service[Service[R]].flatMap(_.completeRecording(gameId, totalMoveCount))
  }

  trait Service[-R] {

    def startRecording(platform: String, playerIds: Seq[String], board: BOARD): ZIO[R, Throwable, GameId]

    def recordMove(gameId: GameId, previousMoveNumber: Int, move: MOVE): ZIO[R, Throwable, Int]

    def completeRecording(gameId: GameId, totalMoveCount: Int): ZIO[R, Throwable, CompleteRecordingGameResult]
  }

}
