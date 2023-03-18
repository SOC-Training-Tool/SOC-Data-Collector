package soc.datacollector

import zio.config._
import ConfigDescriptor._
import com.typesafe.config.{ConfigFactory, ConfigResolveOptions}
import zio.{Has, ZIO, ZLayer}
import zio.config.typesafe._
import zio.config.magnolia.DeriveConfigDescriptor.descriptor
import zio.system.System

object AppConfig {

  val AppConfigPath = "app"
  val ConfigFile = "application.conf"

  val configDescriptor = descriptor[AppConfig].mapKey(toKebabCase)

  val live: ZLayer[Has[System.Service], ReadError[String], Has[AppConfig]] = TypesafeConfig.fromTypesafeConfigM(
    ZIO.service[System.Service].flatMap { sys =>
      sys.envs.flatMap { envs =>
        ZIO.effect(
          ConfigFactory
            .parseString(makeEnvConfig(envs))
            .withFallback(ConfigFactory.parseResources(ConfigFile))
            .resolve(ConfigResolveOptions.noSystem())
            .getConfig(AppConfigPath)
        )
      }.orDie
    },
    configDescriptor)

  private def makeEnvConfig(envs: Map[String, String]): String = {
    envs
      .filter(kv => kv._2.forall(c => !c.isControl))
      .map { case(k, v) => s"""$k = "$v""""}
      .mkString("\n")
  }
}

final case class AppConfig(grpcConfig: GrpcServiceConfig, dbConfig: DbConfig)

final case class GrpcServiceConfig(port: Int)

final case class DbConfig(pgConfig: PgConfig, hikariConfig: Option[HikariConfig])
final case class PgConfig(url: String, userName: String, password: String)
final case class HikariConfig(transactionIsolation: String, maximumPoolSize: Int, minimumIdle: Int, connectionTimeout: Int, maximumLifetime: Int)

