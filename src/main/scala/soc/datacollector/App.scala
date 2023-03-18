package soc.datacollector

import io.github.gaelrenoux.tranzactio.doobie.Database
import soc.datacollector.game.data.GameDataQueries
import zio._
import zio.config.syntax.ZIOConfigNarrowOps

object App extends zio.App {

  private val zenv = ZEnv.any

  private val appConfig = AppConfig.live

  private val dbConfig = appConfig.narrow(_.dbConfig)
  private val grpcConfig = appConfig.narrow(_.grpcConfig)

  private val datasource = (dbConfig ++ zenv) >>> ConnectionPool.live
  private val database = (zenv ++ datasource) >>> Database.fromDatasource

  private val queries = GameDataQueries.live

  private val env = zenv ++ database ++ queries ++ grpcConfig

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    run.exitCode
  }

  def run: ZIO[zio.ZEnv, Throwable, Nothing] = {
    console.putStrLn("starting app") *>
    Server.live.build.useForever.provideLayer(env)
  }
}
