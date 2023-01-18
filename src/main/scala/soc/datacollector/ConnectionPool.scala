package soc.datacollector

import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import zio.{Has, ZIO, ZLayer}
import zio.blocking._

/**
 * Typically, you would use a Connection Pool like HikariCP. Here, we're just gonna use the JDBC H2 datasource directly.
 * Don't do that in production !
 */
object ConnectionPool {

  val live: ZLayer[Has[DbConfig] with Blocking, Throwable, Has[DataSource]] =
    ZLayer.fromServiceM {
    config: DbConfig =>
      effectBlocking {
        val ds = new HikariDataSource()
        ds.setJdbcUrl(config.url)
        ds.setUsername(config.userName)
        ds.setPassword(config.password)
        ds
      }
  }

}