package soc.datacollector.game.store

import soc.datacollector.GameId
import soc.datacollector.game.store.GameRecorderStore.GameRecorderStore
import zio.test.Assertion.isLeft
import zio.test._

object GameRecorderStoreSpec  {

  val InitialGameId = GameId(0)
  val DefaultGameId = GameId(1)

  //val startRecordingSuite = suite("startRecording")()

  def recordMoveSuite[R]: Spec[R with GameRecorderStore[R], TestFailure[Nothing], TestSuccess] = suite("recordMove")(
    testM("fails if gameId does not exist") {
      for {
        result <- GameRecorderStore.recordMove[R](DefaultGameId, 1, ()).either
      } yield assert(result)(isLeft)
    }
  )
}
