package soc.datacollector.game.store

import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database}
import soc.datacollector.game.store.GameRecorderStore.{GameRecorderStore, Service}
import soc.datacollector.game.store.PostgresGameRecorderStore.PgEnv
import soc.datacollector.{ConnectionPool, DbConfig, PgConfig}
import zio.{Has, Tag, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, Spec, TestFailure, TestSuccess, ZSpec}

object PostgresGameRecorderStoreSpec extends DefaultRunnableSpec {


  val layer = {

    //implicit val storeTag

    val dbConfig = ZLayer.succeed(DbConfig(PgConfig("jdbc:postgresql://localhost:5432/mydb", "postgres", "postgres"), None))

    val dataSource = (dbConfig ++ Blocking.any) >>> ConnectionPool.live
    val databaseLayer: ZLayer[Blocking with Clock, Throwable, doobie.Database.Database] = (dataSource ++ Blocking.any ++ Clock.any) >>> Database.fromDatasource
    val storeLayer = ZLayer.succeed[Service[PgEnv]](new PostgresGameRecorderStore())

    val l1 = databaseLayer
    val l2 = l1 ++ storeLayer

    l2.mapError(TestFailure.fail)
  }

  val t = ZLayer.identity[TestEnvironment] ++ layer

  val suite: Spec[PgEnv with GameRecorderStore[PgEnv], TestFailure[Any], TestSuccess] =
    suite("PostgresGameRecorderStoreSpec")(
      GameRecorderStoreSpec.gameRecorderSuites[PgEnv]:_*)

  override def spec: ZSpec[TestEnvironment, Any] =
      suite.provideSomeLayer[TestEnvironment](layer)
}
