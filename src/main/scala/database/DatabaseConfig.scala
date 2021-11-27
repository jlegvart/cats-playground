package io.m99.petstore.config

import cats.effect.Async
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.functor._
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

import scala.concurrent.ExecutionContext
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

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
    config: DatabaseConfig,
    fixedThreadPool: ExecutionContext,
  ): Resource[F, HikariTransactor[F]] = HikariTransactor.newHikariTransactor[F](
    config.driver,
    config.url,
    config.user,
    config.password,
    fixedThreadPool,
  )

  def initializeDb[F[_]](config: DatabaseConfig)(implicit S: Sync[F]): F[Unit] = S
    .delay {
      val fw
        : Flyway = Flyway.configure().dataSource(config.url, config.user, config.password).load()
      fw.migrate()
    }
    .as(())

}
