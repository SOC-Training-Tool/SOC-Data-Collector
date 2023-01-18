package soc.datacollector.game

import io.github.gaelrenoux.tranzactio.doobie.Database
import soc.datacollector.{ConnectionPool, DbConfig, GameId}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.Assertion.isLeft
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Has, ZLayer}

object GameRecorderStoreSpec extends DefaultRunnableSpec {

  val InitialGameId = GameId(0)
  val DefaultGameId = GameId(1)

  override def spec: ZSpec[TestEnvironment, Any] = suite("GameRecorderStore")(postgresGameStoreImplementation)

  //val startRecordingSuite = suite("startRecording")()

  def recordMoveSuite[R]: Spec[R with Has[GameRecorderStore.Service[R]], TestFailure[Nothing], TestSuccess] = suite("recordMove")(
    testM("fails if gameId does not exist") {
      for {
        result <- GameRecorderStore.recordMove[R](DefaultGameId, 1, ()).either
      } yield assert(result)(isLeft)
    }
  )

  val postgresGameStoreImplementation: Spec[Blocking with Clock, TestFailure[Throwable], TestSuccess] = {

    val layer = {
      val dbConfig = ZLayer.succeed(DbConfig("jdbc:postgresql://postgres:5432/mydb", "postgres", "postgres"))

      val dataSource = (dbConfig ++ Blocking.any) >>> ConnectionPool.live
      val databaseLayer = (dataSource ++ Blocking.any ++ Clock.any) >>> Database.fromDatasource
      val storeLayer = GameRecorderStore.postgresStoreLayer

      (databaseLayer ++ storeLayer).mapError(TestFailure.fail)
    }
    suite("Postgres Game Store Implementation")(
      recordMoveSuite[Database.Database])
      .provideSomeLayer[Blocking with Clock](layer)
  }

}
