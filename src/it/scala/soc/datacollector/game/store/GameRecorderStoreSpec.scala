package soc.datacollector.game.store

import soc.datacollector.GameId
import soc.datacollector.domain.{BOARD, MOVE}
import soc.datacollector.game.store.GameRecorderStore.GameRecorderStore
import zio.{Has, Tag}
import zio.test.Assertion.{equalTo, hasField, isLeft, isRight, isTrue}
import zio.test._

object GameRecorderStoreSpec  {

  val invalidGameId = GameId(-1)
  val InitialGameId = GameId(0)
  val DefaultGameId = GameId(1)

  val InitialPreviousMoveNumber = 0

  val platform = "test"
  val playerIds = Seq("player1", "player2", "player3", "player4")

  val board: BOARD = ()
  val move: MOVE = ()

  //val startRecordingSuite = suite("startRecording")()

  def gameRecorderSuites[R <: Has[_]: Tag] = Seq(recordMoveSuite[R], completeRecordingSuite[R])

  private def recordMoveSuite[R <: Has[_]: Tag]: Spec[R with GameRecorderStore[R], TestFailure[Any], TestSuccess] = suite("recordMove")(
    testM("fails if gameId does not exist") {
      val record = GameRecorderStore.recordMove[R](invalidGameId, InitialPreviousMoveNumber, move)
      assertM(record.either)(isLeft)
    },
    testM("fails if previousMoveNumber does not match previously recorded move number") {
      for {
        gameId <- GameRecorderStore.startRecording[R](platform, playerIds, board)
        _ <- GameRecorderStore.recordMove[R](gameId, InitialPreviousMoveNumber, move)
        under <- GameRecorderStore.recordMove[R](gameId, 0, move).either
        over <- GameRecorderStore.recordMove[R](gameId, 10, move).either
      } yield assert(under)(isLeft) && assert(over)(isLeft)
    },
    testM("successfully adds moves to started game") {
      for {
        gameId <- GameRecorderStore.startRecording(platform, playerIds, board)
        n0 <- GameRecorderStore.recordMove[R](gameId, InitialPreviousMoveNumber, move)
        n1 <- GameRecorderStore.recordMove[R](gameId, n0, move)
        n2 <- GameRecorderStore.recordMove[R](gameId, n1, move)
        n3 <- GameRecorderStore.recordMove[R](gameId, n2, move)
        n4 <- GameRecorderStore.recordMove[R](gameId, n3, move)
      } yield assert(n4)(equalTo(5))
    }
  )

  private def completeRecordingSuite[R <: Has[_]: Tag]: Spec[R with GameRecorderStore[R], TestFailure[Any], TestSuccess] = suite("completeRecording")(
    testM("fails if gameId does not exist") {
      assertM(GameRecorderStore.completeRecording[R](invalidGameId, 0).either)(isLeft)
    },
    testM("fails if totalMoveCount does not equal number of moves recorded") {
      for {
        gameId <- GameRecorderStore.startRecording[R](platform, playerIds, board)
        _ <- GameRecorderStore.recordMove[R](gameId, InitialPreviousMoveNumber, move)
        result <- GameRecorderStore.completeRecording[R](gameId, 0).either
      } yield assert(result)(isLeft)
    },
    testM("successfully completes game") {
      for {
        gameId <- GameRecorderStore.startRecording[R](platform, playerIds, board)
        n0 <- GameRecorderStore.recordMove[R](gameId, InitialPreviousMoveNumber, move)
        n1 <- GameRecorderStore.recordMove[R](gameId, n0, move)
        n2 <- GameRecorderStore.recordMove[R](gameId, n1, move)
        n3 <- GameRecorderStore.recordMove[R](gameId, n2, move)
        n4 <- GameRecorderStore.recordMove[R](gameId, n3, move)
        result <- GameRecorderStore.completeRecording[R](gameId, n4 + 1).either
      } yield assert(result)(isRight(hasField("numMoves", _.numMoves, equalTo(n4 + 1))))
    }
  )

}
