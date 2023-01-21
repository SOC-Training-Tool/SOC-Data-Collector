package soc.datacollector

final case class DbConfig(pgConfig: PgConfig, hikariConfig: Option[HikariConfig])
final case class PgConfig(url: String, userName: String, password: String)
final case class HikariConfig(transactionIsolation: String, maximumPoolSize: Int, minimumIdle: Int, connectionTimeout: Int, maximumLifetime: Int)
