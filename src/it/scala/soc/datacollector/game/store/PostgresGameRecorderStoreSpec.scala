package soc.datacollector.game.store

import io.github.gaelrenoux.tranzactio.doobie.Database
import soc.datacollector.{ConnectionPool, DbConfig}
import zio.ZLayer
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.{DefaultRunnableSpec, TestFailure, ZSpec}

object PostgresGameRecorderStoreSpec extends DefaultRunnableSpec {

  val layer = {
    val dbConfig = ZLayer.succeed(DbConfig("jdbc:postgresql://postgres:5432/mydb", "postgres", "postgres"))

    val dataSource = (dbConfig ++ Blocking.any) >>> ConnectionPool.live
    val databaseLayer = (dataSource ++ Blocking.any ++ Clock.any) >>> Database.fromDatasource
    val storeLayer = PostgresGameRecorderStore.live

    (databaseLayer ++ storeLayer).mapError(TestFailure.fail)
  }

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("PostgresGameRecorderStoreSpec")(GameRecorderStoreSpec.recordMoveSuite[Database.Database])
      .provideSomeLayerShared[Clock with Blocking](layer)
}
