package soc.datacollector

import io.github.gaelrenoux.tranzactio.doobie.Database
import soc.datacollector.game.data.GameDataQueries
import zio._

object App extends zio.App {

  private val dbConfig = ZLayer.succeed(DbConfig(PgConfig("jdbc:postgresql://localhost:5432/mydb", "postgres", "postgres"), None))

  private val zenv = ZEnv.any

  private val datasource = (dbConfig ++ zenv) >>> ConnectionPool.live
  private val database = (zenv ++ datasource) >>> Database.fromDatasource

  private val queries = GameDataQueries.live

  private val env = zenv ++ database ++ queries

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    run.exitCode
  }

  def run: ZIO[zio.ZEnv, Throwable, Nothing] = {
    console.putStrLn("starting app") *>
    Server.live.build.useForever.provideLayer(env)
  }
}
