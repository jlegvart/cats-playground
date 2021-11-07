package webcrawler

import cats.effect.kernel.Async
import cats.effect.std.Queue
import cats.effect.syntax._
import cats.effect.std.Console
import cats.syntax.all._
import org.http4s.client.blaze._
import org.http4s.client._
import scala.concurrent.ExecutionContext.Implicits.global
import org.http4s.client.dsl.io._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import java.util.regex.Pattern
import java.util.regex.Matcher

object WebCrawler {

  class CrawlerResult(url: String, content: String)

  def apply[F[_]: Async: Console](client: Client[F]): WebCrawler[F] = new WebCrawler[F](client)
}

class WebCrawler[F[_]: Async: Console](client: Client[F]) {

  val pattern = "href=\"(\\/wiki\\/.*?)\"".r;
  val wiki = ""

  def start(url: String) =
    for {
      urlQ <- Queue.unbounded[F, String]
      resQ <- Queue.unbounded[F, WebCrawler.CrawlerResult]

      host <- Async[F].pure(
        Uri
          .fromString(url)
          .flatMap(a => a.host.toRight[Exception](new RuntimeException("Invalid url")))
      )
      hostStr <- host.fold(Async[F].raiseError(_), Async[F].pure(_))

      _ <- Console[F].println(s"Host: ${hostStr}")
      - <- urlQ.offer(url)
      _ <- crawl(urlQ, resQ)
    } yield ()

  def crawl(urlQueue: Queue[F, String], results: Queue[F, WebCrawler.CrawlerResult]): F[Unit] =
    for {
      next <- urlQueue.take
      _ <- Console[F].println(s"Crawling next url: $next")

      result <- client.expect[String](next)

      a =
        pattern
          .findAllMatchIn(result)
          .map(_.group(1))
          .toSeq

      _ <- Console[F].println(a)
    } yield ()

}
