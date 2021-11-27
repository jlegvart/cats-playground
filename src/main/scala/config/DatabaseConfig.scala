package config

import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.functor._
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

case class DatabaseConnectionsConfig(poolSize: Int)

case class DatabaseConfig(
  url: String,
  driver: String,
  user: String,
  password: String,
  connections: DatabaseConnectionsConfig,
)

object DatabaseConfig {

  def transactor[F[_]: Async](
    appName: String,
    config: DatabaseConfig,
    fixedThreadPool: ExecutionContext,
  ): Resource[F, HikariTransactor[F]] = HikariTransactor.newHikariTransactor[F](
    config.driver,
    config.url + appName,
    config.user,
    config.password,
    fixedThreadPool,
  )

  def initializeDb[F[_]](appName: String, config: DatabaseConfig)(implicit S: Sync[F]): F[Unit] = S
    .delay {
      val fw: Flyway = Flyway
        .configure()
        .dataSource(config.url + appName, config.user, config.password)
        .locations(s"classpath:/db/migration/$appName")
        .load()
      fw.migrate()
    }
    .as(())

}
