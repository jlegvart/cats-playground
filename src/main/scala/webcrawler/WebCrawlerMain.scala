package webcrawler

import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import org.http4s.client.blaze._
import org.http4s.client._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.Uri
import org.http4s.ParseFailure
import cats.data.EitherT

object WebCrawlerMain extends IOApp {

  override def run(
    args: List[String]
  ): IO[ExitCode] =
    for {
      url <- checkUrl(args).flatMap {
        case Right(url)      => IO.pure(url)
        case Left(exception) => IO.raiseError(exception)
      }
      _ <- BlazeClientBuilder[IO](global).resource.use(startCrawler(url, _))
    } yield ExitCode.Success

  def startCrawler(uri: Uri, client: Client[IO]): IO[Unit] = {
    val crawler = WebCrawler(client)
    crawler
      .start(uri)
      .onError(_ => IO.println("Error during crawler starting"))
  }

  def checkUrl(args: List[String]): IO[Either[ParseFailure, Uri]] =
    for {
      _ <-
        if (args.length < 1)
          IO.raiseError(new RuntimeException("No url provided"))
        else
          IO.unit
      uriFromStr = Uri.fromString(args(0))
    } yield uriFromStr

}
