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

  val dbDriver = "org.postgresql.Driver"
  val dbName = "crawler"
  val dbUsername = "postgres"
  val dbPassword = "postgres"
  val dbUrl = s"jdbc:postgresql://localhost:5432/$dbName"

  val numOfCrawlers = 2

  override def run(
    args: List[String]
  ): IO[ExitCode] =
    (for {
      seed <- Resource.liftK(parseSeed(args))
      transactor <- transactor
      repository = new DoobieRepository(transactor)
      crawler <- initCrawler(seed, transactor, repository)
    } yield crawler)
      .use(_.start)
      .as(ExitCode.Success)

  def initCrawler(
    seed: Uri,
    transactor: HikariTransactor[IO],
    repository: DoobieRepository[IO],
  ): Resource[IO, WebCrawler[IO]] =
    for {
      client <- BlazeClientBuilder[IO](global).resource
      crawler <- Resource.liftK(IO.pure(WebCrawler(seed, client, repository, numOfCrawlers)))
    } yield crawler

  def selectData(repository: DoobieRepository[IO]) =
    for {
      list <- repository.select
      _ <- IO.println(list)
    } yield ()

  def parseSeed(args: List[String]): IO[Uri] =
    if (args.length < 1 || !args(0).startsWith("http")) {
      IO.raiseError(new RuntimeException("Invalid url specified"))
    } else {
      Uri.fromString(args(0)) match {
        case Left(failure) =>
          IO.raiseError(new RuntimeException(s"Error during parsing uri string: $failure"))
        case Right(uri) => IO.pure(uri)
      }
    }

  def transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      fixedThreadPool <- ExecutionContexts.fixedThreadPool[IO](12)
      transactor <- HikariTransactor.newHikariTransactor[IO](
        dbDriver,
        dbUrl,
        dbUsername,
        dbPassword,
        fixedThreadPool,
      )
      _ <- Resource.liftK(initializeDb)
    } yield transactor

  def initializeDb: IO[Unit] = IO
    .delay {
      val fw: Flyway = Flyway.configure().dataSource(dbUrl, dbUsername, dbPassword).load()
      fw.migrate()
    }
    .as(())

}
