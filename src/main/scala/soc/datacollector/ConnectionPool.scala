package soc.datacollector

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import javax.sql.DataSource
import zio.{Has, ZIO, ZLayer, ZManaged}
import zio.blocking._
import zio.duration.durationInt

/**
 * Typically, you would use a Connection Pool like HikariCP. Here, we're just gonna use the JDBC H2 datasource directly.
 * Don't do that in production !
 */
object ConnectionPool {

  private val LeakThreshold = 2.seconds

  val live: ZLayer[Blocking with Has[DbConfig], Throwable, Has[DataSource]] =
    ZIO.service[DbConfig].flatMap {
      case DbConfig(config, hikariConfigOpt) =>
        effectBlocking {
          val hikariConfig: HikariConfig = new HikariConfig()
          hikariConfig.setJdbcUrl(config.url)
          hikariConfig.setDriverClassName("org.postgresql.Driver")
          hikariConfig.setUsername(config.userName)
          hikariConfig.setPassword(config.password)
          hikariConfigOpt.foreach { hkOptions =>
            hikariConfig.setLeakDetectionThreshold(LeakThreshold.toMillis)
            hikariConfig.setTransactionIsolation(hkOptions.transactionIsolation)
            hikariConfig.setConnectionTimeout(hkOptions.connectionTimeout)
            hikariConfig.setMaxLifetime(hkOptions.maximumLifetime)
            hikariConfig.setMinimumIdle(hkOptions.minimumIdle)
            hikariConfig.setMaximumPoolSize(hkOptions.maximumPoolSize)
          }
          new HikariDataSource(hikariConfig)
        }
    }.toManaged(ds => ZIO.effectTotal(ds.close()))
      .toLayer

}