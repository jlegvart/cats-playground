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

import io.circe.generic.auto._
import io.circe.config.syntax._
import io.circe.config.parser
import io.m99.petstore.config.DatabaseConfig

object WebCrawlerMain extends IOApp {

  val numOfCrawlers = 2

  override def run(
    args: List[String]
  ): IO[ExitCode] =
    (for {
      seed <- Resource.liftK(parseSeed(args))
      config <- Resource.liftK(dbConfig)
      fixedThreadPool <- ExecutionContexts.fixedThreadPool[IO](config.connections.poolSize)
      transactor <- DatabaseConfig.transactor[IO](config, fixedThreadPool)
      initDb <- Resource.liftK(DatabaseConfig.initializeDb[IO](config))
      repository = new DoobieRepository(transactor)
      // crawler <- initCrawler(seed, transactor, repository)
      select <- Resource.liftK(selectData(repository))
    } yield select)
      .use(_ => IO.unit)
      .as(ExitCode.Success)

  def dbConfig =
    (for {
      databaseConfig <- parser.decodePath[DatabaseConfig]("database")
    } yield databaseConfig) match {
      case Right(config) => IO.pure(config)
      case Left(error)   => IO.raiseError(error)
    }

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
      _ <- IO.delay(list.foreach(item => println(item.title)))
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

}
