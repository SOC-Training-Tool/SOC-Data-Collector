package soc.datacollector.game.store

import io.soc.core.base.board.Vertex
import io.soc.core.base.moves.BuildSettlement
import io.soc.recorder.game_recorder.MoveEvent
import io.soc.recorder.game_recorder.MoveEvent.Move.BuildSettlementMove
import soc.datacollector.game.store.GameRecorderStore.Service
import soc.datacollector.{Board, GameId, Move}
import soc.datacollector.player.PlayerId
import zio.{Has, Tag}
import zio.test.Assertion.{equalTo, hasField, isLeft, isNone, isRight, isSome, isTrue}
import zio.test._

object GameRecorderStoreSpec  {

  val invalidGameId = GameId(-1)
  val InitialGameId = GameId(0)
  val DefaultGameId = GameId(1)

  val InitialPreviousMoveNumber = 0

  val platform = "test"
  val playerIds = Seq("player1", "player2", "player3", "player4").map(PlayerId.apply)

  val board = Board()
  val move: Move = Move(MoveEvent(BuildSettlementMove(BuildSettlement(Some(Vertex("1"))))))

  //val startRecordingSuite = suite("startRecording")()

  def gameRecorderSuites[R <: Has[_]: Tag, S <: GameRecorderStore.Service[R]: Tag]: Seq[Spec[Has[S] with R, TestFailure[Throwable], TestSuccess]] = Seq(recordMoveSuite[R, S], completeRecordingSuite[R, S], getLatestMoveSuite[R, S])

  private def recordMoveSuite[R <: Has[_]: Tag, S <: GameRecorderStore.Service[R]: Tag] = suite("recordMove")(
    testM("fails if gameId does not exist") {
      val record = GameRecorderStore.recordMove[R, S](invalidGameId, InitialPreviousMoveNumber, move)
      assertM(record.either)(isLeft)
    },
    testM("fails if previousMoveNumber does not match previously recorded move number") {
      for {
        gameId <- GameRecorderStore.startRecording[R, S](platform, playerIds, board)
        _ <- GameRecorderStore.recordMove[R, S](gameId, InitialPreviousMoveNumber, move)
        under <- GameRecorderStore.recordMove[R, S](gameId, 0, move).either
        over <- GameRecorderStore.recordMove[R, S](gameId, 10, move).either
      } yield assert(under)(isLeft) && assert(over)(isLeft)
    },
    testM("successfully adds moves to started game") {
      for {
        gameId <- GameRecorderStore.startRecording[R, S](platform, playerIds, board)
        n0 <- GameRecorderStore.recordMove[R, S](gameId, InitialPreviousMoveNumber, move)
        n1 <- GameRecorderStore.recordMove[R, S](gameId, n0, move)
        n2 <- GameRecorderStore.recordMove[R, S](gameId, n1, move)
        n3 <- GameRecorderStore.recordMove[R, S](gameId, n2, move)
        n4 <- GameRecorderStore.recordMove[R, S](gameId, n3, move)
      } yield assert(n4)(equalTo(5))
    }
  )

  private def completeRecordingSuite[R <: Has[_]: Tag, S <: GameRecorderStore.Service[R]: Tag] = suite("completeRecording")(
    testM("fails if gameId does not exist") {
      assertM(GameRecorderStore.completeRecording[R, S](invalidGameId, 0).either)(isLeft)
    },
    testM("fails if totalMoveCount does not equal number of moves recorded") {
      for {
        gameId <- GameRecorderStore.startRecording[R, S](platform, playerIds, board)
        _ <- GameRecorderStore.recordMove[R, S](gameId, InitialPreviousMoveNumber, move)
        result <- GameRecorderStore.completeRecording[R, S](gameId, 0).either
      } yield assert(result)(isLeft)
    },
    testM("successfully completes game") {
      for {
        gameId <- GameRecorderStore.startRecording[R, S](platform, playerIds, board)
        n0 <- GameRecorderStore.recordMove[R, S](gameId, InitialPreviousMoveNumber, move)
        n1 <- GameRecorderStore.recordMove[R, S](gameId, n0, move)
        n2 <- GameRecorderStore.recordMove[R, S](gameId, n1, move)
        n3 <- GameRecorderStore.recordMove[R, S](gameId, n2, move)
        n4 <- GameRecorderStore.recordMove[R, S](gameId, n3, move)
        result <- GameRecorderStore.completeRecording[R, S](gameId, n4).either
      } yield assert(result)(isRight(hasField("numMoves", _.numMoves, equalTo(n4))))
    }
  )

  private def getLatestMoveSuite[R <: Has[_]: Tag, S <: Service[R]: Tag] = suite("getLastRecordedMove")(
    testM("returns None for invalid gameId") {
      assertM(GameRecorderStore.getLatestMove[R, S](invalidGameId).either)(isRight(isNone))
    },
    testM("returns last recorded move") {
      for {
        gameId <- GameRecorderStore.startRecording[R, S](platform, playerIds, board)
        n0 <- GameRecorderStore.recordMove[R, S](gameId, InitialPreviousMoveNumber, move)
        n1 <- GameRecorderStore.recordMove[R, S](gameId, n0, move)
        n2 <- GameRecorderStore.recordMove[R, S](gameId, n1, move)
        lastMove <- GameRecorderStore.getLatestMove[R, S](gameId)
      } yield assert(lastMove)(isSome(hasField("lastMoveNumber", _.latestMoveNumber, equalTo(n2))))
    },
    testM("returns None when game has completed recording") {
      for {
        gameId <- GameRecorderStore.startRecording[R, S](platform, playerIds, board)
        n0 <- GameRecorderStore.recordMove[R, S](gameId, InitialPreviousMoveNumber, move)
        n1 <- GameRecorderStore.recordMove[R, S](gameId, n0, move)
        n2 <- GameRecorderStore.recordMove[R, S](gameId, n1, move)
        _ <- GameRecorderStore.completeRecording[R, S](gameId, n2)
        lastMove <- GameRecorderStore.getLatestMove[R, S](gameId)
      } yield assert(lastMove)(isNone)
    }
  )
}
