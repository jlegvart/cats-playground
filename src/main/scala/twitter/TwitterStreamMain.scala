package twitter

import cats.effect.IOApp
import cats.effect.IO
import org.http4s.client.oauth1
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import scala.concurrent.ExecutionContext.global
import cats.effect.kernel.Resource
import doobie.util.ExecutionContexts
import config.AppConfig
import config.DatabaseConfig
import scala.concurrent.ExecutionContext
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.effect.kernel.Sync
import org.typelevel.log4cats.Logger

object TwitterStreamMain extends IOApp.Simple {

  implicit def unsafeLogger = Slf4jLogger.getLogger[IO]

  val url = "https://stream.twitter.com/1.1/statuses/filter.json?track=covid19"

  def run: IO[Unit] =
    Logger[IO].info("Starting TwitterStreaming app") >> (for {
      appConfig <- Resource.liftK(AppConfig.loadConfig[IO]())
      appName = appConfig.twitter.name
      databaseConfig = appConfig.database
      fixedThreadPool <- ExecutionContexts.fixedThreadPool[IO](databaseConfig.connections.poolSize)
      transactor <- DatabaseConfig.transactor[IO](
        appName,
        databaseConfig,
        fixedThreadPool,
      )
      initDb <- Resource.liftK(DatabaseConfig.initializeDb[IO](appName, databaseConfig))
      u <- Resource.liftK(uri)
      client <- BlazeClientBuilder[IO](global).resource
    } yield (u, client)).use(res => TWStream(res._1, res._2).stream.compile.drain)

  def uri: IO[Uri] =
    Uri.fromString(url) match {
      case Left(error) =>
        IO.raiseError(new RuntimeException(s"Error parsing url: ${error.message}"))
      case Right(uri) => IO.pure(uri)
    }

}
