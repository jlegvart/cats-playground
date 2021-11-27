package database

import io.circe._
import io.circe.generic.auto._
import io.circe.config.parser
import io.circe.syntax._
import cats.effect.Sync


final case class AppConfig(database: DatabaseConfig, crawler: WebCrawler, twitter: Twitter)
final case class WebCrawler(name: String)
final case class Twitter(name: String)

object AppConfig {

  def loadConfig[F[_]: Sync]() =
    (for {
      config <- parser.decodePath[AppConfig]("appConfig")
    } yield config) match {
      case Right(config) => Sync[F].pure(config)
      case Left(error)   => Sync[F].raiseError(error)
    }

}
