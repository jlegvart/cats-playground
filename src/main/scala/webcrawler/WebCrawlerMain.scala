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
import webcrawler.repository.DoobieRepository

import doobie.Transactor
import org.http4s.ParseResult

object WebCrawlerMain extends IOApp {

  val dbName = "crawler"
  val dbUsername = "postgres"
  val dbPassword = "postgres"

  override def run(
    args: List[String]
  ): IO[ExitCode] =
    for {
      seed <- checkUrl(args).flatMap {
        case Right(url) => IO.pure(url)
        case Left(failure) =>
          IO.raiseError(new RuntimeException(s"Error parsing url: ${failure.message}"))
      }

      xa = Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        s"jdbc:postgresql://localhost:5432/$dbName",
        dbUsername,
        dbPassword,
      )

      repository = new DoobieRepository(xa)
      _ <- BlazeClientBuilder[IO](global).resource.use(startCrawler(seed, _, repository))
    } yield ExitCode.Success

  def startCrawler(seed: Uri, client: Client[IO], repository: DoobieRepository[IO]): IO[Unit] = {
    val crawler = WebCrawler(client, repository)
    crawler
      .start(seed)
      .onError(_ => IO.println("Error during crawler starting"))
  }

  def checkUrl(args: List[String]): IO[Either[ParseFailure, Uri]] =
    for {
      uri <-
        if (args.length < 1 || !args(0).startsWith("http"))
          IO.pure(ParseResult.fail(args(0), "Invalid url"))
        else
          IO.pure(Uri.fromString(args(0)))
    } yield uri

}
