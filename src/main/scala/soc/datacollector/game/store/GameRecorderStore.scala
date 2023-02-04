package soc.datacollector.game.store


import soc.datacollector.{Board, GameId, Move}
import soc.datacollector.game.{CompleteRecordingGameResult, LatestMove, StartGameData}
import soc.datacollector.player.PlayerId
import zio.duration.Duration
import zio.{Has, Tag, ULayer, ZIO, ZLayer}

object GameRecorderStore {

  type GameRecorderStore[R <: Has[_]] = Has[Service[R]]

  def startRecording[R <: Has[_]: Tag, S <: Service[R]: Tag](platform: String, playerIds: Seq[PlayerId], board: Board): ZIO[Has[S] with R, Throwable, GameId] = {
    val f = (_: S).startRecording(platform, playerIds, board)
    ZIO.service[S].flatMap(f)
  }

  def recordMove[R <: Has[_]: Tag, S <: Service[R]: Tag](gameId: GameId, previousMoveNumber: Int, move: Move): ZIO[Has[S] with R, Throwable, Int] = {
    val f = (_: S).recordMove(gameId, previousMoveNumber, move)
    ZIO.service[S].flatMap(f)
  }

  def completeRecording[R <: Has[_]: Tag, S <: Service[R]: Tag](gameId: GameId, totalMoveCount: Int): ZIO[Has[S] with R, Throwable, CompleteRecordingGameResult] = {
   val f = (_: S).completeRecording(gameId, totalMoveCount)
    ZIO.service[S].flatMap(_.completeRecording(gameId, totalMoveCount))
  }

  def getLatestMove[R <: Has[_]: Tag, S <: Service[R]: Tag](gameId: GameId): ZIO[R with Has[S], Throwable, Option[LatestMove]] = {
    ZIO.service[S].flatMap(_.getLastRecordedMove(gameId))
  }

//  def getStaleGames[R <: Has[_]: Tag, S <: Service[R]: Tag](staleDuration: Duration): ZIO[R with Has[S], Throwable, List[LatestMove]] =
//    ZIO.service[S].flatMap(_.getStaleGames(staleDuration))

  trait Service[R <: Has[_]] {

    def startRecording(platform: String, playerIds: Seq[PlayerId], board: Board): ZIO[R, Throwable, GameId]

    def recordMove(gameId: GameId, previousMoveNumber: Int, move: Move): ZIO[R, Throwable, Int]

    def completeRecording(gameId: GameId, totalMoveCount: Int): ZIO[R, Throwable, CompleteRecordingGameResult]

    def getLastRecordedMove(gameId: GameId): ZIO[R, Throwable, Option[LatestMove]]

   // def getStaleGames(staleDuration: Duration): ZIO[R, Throwable, List[LatestMove]]
  }

}
