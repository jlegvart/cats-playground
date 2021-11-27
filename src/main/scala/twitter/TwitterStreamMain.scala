package twitter

import cats.effect.IOApp
import cats.effect.IO
import org.http4s.client.oauth1
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder
import scala.concurrent.ExecutionContext.global
import cats.effect.kernel.Resource
import doobie.util.ExecutionContexts
import config.{AppConfig, DatabaseConfig}
import scala.concurrent.ExecutionContext

object TwitterStreamMain extends IOApp.Simple {

  val url = "https://stream.twitter.com/1.1/statuses/filter.json?track=covid19"

  def run: IO[Unit] =
    (for {
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
