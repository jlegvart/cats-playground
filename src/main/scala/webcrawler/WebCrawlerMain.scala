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
import org.flywaydb.core.Flyway
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import doobie.util.transactor
import cats.effect.kernel.Resource

object WebCrawlerMain extends IOApp {

  val dbName = "crawler"
  val dbUsername = "postgres"
  val dbPassword = "postgres"
  val dbUrl = s"jdbc:postgresql://localhost:5432/$dbName"

  override def run(
    args: List[String]
  ): IO[ExitCode] = {
    val resource =
      for {
        seed <- Resource.liftK(parseSeed(args))
        transactor <- transactorResource()
        client <- BlazeClientBuilder[IO](global).resource
        exit <- Resource.liftK(startCrawler(seed, client, new DoobieRepository(transactor)))
      } yield exit

    resource.use(_ => IO.pure(ExitCode.Success))
  }

  def parseSeed(args: List[String]) = checkUrl(args).flatMap {
    case Right(url) => IO.pure(url)
    case Left(failure) =>
      IO.raiseError(new RuntimeException(s"Error parsing url: ${failure.message}"))
  }

  def transactorResource(): Resource[IO, HikariTransactor[IO]] =
    for {
      fixedThreadPool <- ExecutionContexts.fixedThreadPool[IO](12)
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        dbUrl,
        dbUsername,
        dbPassword,
        fixedThreadPool,
      )
      _ <- Resource.liftK(initializeDb())
    } yield transactor

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

  def initializeDb(): IO[Unit] = IO
    .delay {
      val fw: Flyway = Flyway.configure().dataSource(dbUrl, dbUsername, dbPassword).load()
      fw.migrate()
    }
    .as(())

}
