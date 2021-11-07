package webcrawler

import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import org.http4s.client.blaze._
import org.http4s.client._
import scala.concurrent.ExecutionContext.Implicits.global

object WebCrawlerMain extends IOApp {

  override def run(
    args: List[String]
  ): IO[ExitCode] =
    for {
      _ <- BlazeClientBuilder[IO](global).resource.use { client =>
        startCrawler(client) *> IO.unit
      }

    } yield ExitCode.Success

  def startCrawler(client: Client[IO]): IO[Unit] = {
    val crawler = WebCrawler(client)
    crawler
      .start("https://en.m.wikipedia.org/wiki/Gerald_Weinberg")
      .onError(_ => IO.println("Error during crawler starting"))
  }

}
