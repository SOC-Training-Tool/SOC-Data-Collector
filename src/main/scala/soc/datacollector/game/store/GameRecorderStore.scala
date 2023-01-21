package soc.datacollector.game.store


import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE, PlayerId}
import soc.datacollector.game.{CompleteRecordingGameResult, StartGameData}
import zio.{Has, Tag, ULayer, ZIO, ZLayer}

object GameRecorderStore {

  type GameRecorderStore[R <: Has[_]] = Has[Service[R]]

  def startRecording[R <: Has[_]: Tag](platform: String, playerIds: Seq[String], board: BOARD): ZIO[R with GameRecorderStore[R], Throwable, GameId] = {
    val f = (_: Service[R]).startRecording(platform, playerIds, board)
    ZIO.service[Service[R]].flatMap(f)
  }

  def recordMove[R <: Has[_]: Tag](gameId: GameId, previousMoveNumber: Int, move: MOVE): ZIO[R with GameRecorderStore[R], Throwable, Int] = {
    val f = (_: Service[R]).recordMove(gameId, previousMoveNumber, move)
    ZIO.service[Service[R]].flatMap(f)
  }

  def completeRecording[R <: Has[_]: Tag](gameId: GameId, totalMoveCount: Int): ZIO[R with GameRecorderStore[R], Throwable, CompleteRecordingGameResult] = {
   val f = (_: Service[R]).completeRecording(gameId, totalMoveCount)
    ZIO.service[Service[R]].flatMap(_.completeRecording(gameId, totalMoveCount))
  }

  trait Service[R <: Has[_]] {

    def startRecording(platform: String, playerIds: Seq[String], board: BOARD): ZIO[R, Throwable, GameId]

    def recordMove(gameId: GameId, previousMoveNumber: Int, move: MOVE): ZIO[R, Throwable, Int]

    def completeRecording(gameId: GameId, totalMoveCount: Int): ZIO[R, Throwable, CompleteRecordingGameResult]
  }

}
