package soc.datacollector.game.store

import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database}
import io.github.scottweaver.zio.aspect.DbMigrationAspect
import io.github.scottweaver.zio.testcontainers.postgres.ZPostgreSQLContainer
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

    val postgres = ZPostgreSQLContainer.Settings.default >>> ZPostgreSQLContainer.live

    //val dbConfig = ZLayer.succeed(DbConfig(PgConfig("jdbc:postgresql://localhost:5432/mydb", "postgres", "postgres"), None))

    //val dataSource = (dbConfig ++ Blocking.any) >>> ConnectionPool.live
    val databaseLayer: ZLayer[Blocking with Clock, Throwable, doobie.Database.Database] = (postgres ++ Blocking.any ++ Clock.any) >>> Database.fromDatasource
    val storeLayer = ZLayer.succeed(new PostgresGameRecorderStore())

    (postgres ++ databaseLayer ++ storeLayer).mapError(TestFailure.fail)
  }

  val suite: Spec[Has[PostgresGameRecorderStore] with PgEnv, TestFailure[Throwable], TestSuccess] =
    suite("PostgresGameRecorderStoreSpec")(
      GameRecorderStoreSpec.gameRecorderSuites[PgEnv, PostgresGameRecorderStore]:_*)

  override def spec: ZSpec[TestEnvironment, Any] =
    (suite @@ DbMigrationAspect.migrateOnce("filesystem:db/migration")()).provideSomeLayerShared[TestEnvironment](layer)
}
