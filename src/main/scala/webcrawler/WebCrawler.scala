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
import org.jsoup.Jsoup
import collection.JavaConverters._
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.kernel.Ref
import scala.concurrent.duration._

case class CrawlerResult(url: String, title: String, content: String)

object WebCrawler {
  def apply[F[_]: Async: Console](client: Client[F]): WebCrawler[F] = new WebCrawler[F](client)
}

class WebCrawler[F[_]: Async: Console](client: Client[F]) {

  def start(seed: Uri) =
    for {
      urlQ <- Queue.unbounded[F, Uri]
      resQ <- Ref.of[F, List[CrawlerResult]](List())

      _ <- Console[F].println(s"Starting scraping: ${seed.toString()}")
      - <- urlQ.offer(seed)
      _ <- crawl(urlQ, resQ)
    } yield ()

  def crawl(urlQueue: Queue[F, Uri], listR: Ref[F, List[CrawlerResult]]): F[Unit] =
    for {
      next <- urlQueue.take
      _ <- Console[F].println(s"Crawling next url: ${next.toString()}")

      host <- getFullHost(next)
      html <- client.expect[String](next)
      htmlDocument = Jsoup.parse(html)
      title <- title(htmlDocument)
      content <- parseContent(htmlDocument)
      links <- parseLinks(host, htmlDocument)

      _ <- links.traverse(urlQueue.offer(_))
      _ <- listR.modify { list =>
        val item = CrawlerResult(next.toString(), title, content)
        (list :+ item, item)
      }
      _ <- Async[F].sleep(500.milliseconds)
      _ <- crawl(urlQueue, listR)
    } yield ()

  def getFullHost(url: Uri) =
    (url.host, url.scheme) match {
      case (Some(host), Some(scheme)) =>
        Async[F].pure(scheme.value.toString().concat("://").concat(host.toString()))
      case _ =>
        Async[F].raiseError(
          new RuntimeException(s"Invalid url, cannot extract scheme & host from: $url")
        )
    }

  def title(html: Document): F[String] = Async[F].pure(html.title())

  def parseLinks(host: String, html: Document): F[Seq[Uri]] = Async[F].pure(
    html
      .select("#bodyContent a[href*=\"/wiki/\"]")
      .eachAttr("href")
      .asScala
      .toSeq
      .filter(!_.contains(":"))
      .map {
        case x if x.startsWith("http") => Some(x)
        case x if x.startsWith("/")    => Some(host.concat(x))
        case _                         => None
      }
      .flatten
      .map(Uri.fromString(_).toOption)
      .flatten
      .distinct
  )

  def parseContent(html: Document): F[String] = Async[F].pure(html.text())

}