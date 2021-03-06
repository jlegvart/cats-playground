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
import webcrawler.repository.DoobieRepository
import cats.effect.kernel.MonadCancel
import cats.Parallel

case class CrawlerResult(id: Option[Long] = None, url: String, title: String, content: String)

object WebCrawler {

  def apply[F[_]: Async: Parallel: Console](
    seed: Uri,
    client: Client[F],
    repository: DoobieRepository[F],
    numOfCrawlers: Int,
  ): WebCrawler[F] = new WebCrawler[F](seed, client, repository, numOfCrawlers)

}

class WebCrawler[F[_]: Async: Parallel: Console](
  seed: Uri,
  client: Client[F],
  repository: DoobieRepository[F],
  numOfCrawlers: Int,
) {

  def start =
    for {
      urlQ <- Queue.unbounded[F, Uri]
      resQ <- Ref.of[F, List[CrawlerResult]](List())
      crawled <- Ref.of[F, Set[String]](Set())

      _ <- urlQ.offer(seed)
      crawlers = List.range(1, numOfCrawlers + 1).map(crawl(_, urlQ, resQ, crawled))
      _ <- crawlers.parSequence
    } yield ()

  def crawl(
    crawlerId: Int,
    urlQueue: Queue[F, Uri],
    listR: Ref[F, List[CrawlerResult]],
    crawledR: Ref[F, Set[String]],
  ): F[Unit] =
    MonadCancel[F].uncancelable { _ =>
      parseNext(crawlerId, urlQueue, listR, crawledR)
    } >> Async[F].sleep(1.second) >> crawl(crawlerId, urlQueue, listR, crawledR)

  def parseNext(
    crawlerId: Int,
    urlQueue: Queue[F, Uri],
    listR: Ref[F, List[CrawlerResult]],
    crawledR: Ref[F, Set[String]],
  ): F[Unit] =
    for {
      next <- takeNext(urlQueue, crawledR)
      _ <- Console[F].println(s"$crawlerId: Crawling next url: ${next.toString()}")

      host <- getFullHost(next)
      html <- client.expect[String](next)
      htmlDocument = Jsoup.parse(html)

      result <- getCrawlerResult(next.toString(), htmlDocument)
      links <- parseLinks(host, htmlDocument)

      _ <- links.traverse(urlQueue.offer(_))
      _ <- listR.modify { list =>
        (list :+ result, Async[F].unit)
      }
      _ <- repository.insert(result)
    } yield ()

  def takeNext(urlQ: Queue[F, Uri], crawledR: Ref[F, Set[String]]): F[Uri] =
    for {
      next <- urlQ.take
      alreadyContains <- crawledR.modify { set =>
        if (set.contains(next.toString()))
          (set, true)
        else
          (set + next.toString(), false)
      }
      _ <-
        if (alreadyContains)
          takeNext(urlQ, crawledR)
        else
          Async[F].unit
    } yield next

  def getFullHost(url: Uri) =
    (url.host, url.scheme) match {
      case (Some(host), Some(scheme)) =>
        Async[F].pure(scheme.value.toString().concat("://").concat(host.toString()))
      case _ =>
        Async[F].raiseError(
          new RuntimeException(s"Invalid url, cannot extract scheme & host from: $url")
        )
    }

  def getCrawlerResult(url: String, html: Document): F[CrawlerResult] =
    for {
      title <- Async[F].pure(html.title())
      content <- Async[F].pure(html.text())
    } yield (CrawlerResult(url = url, title = title, content = content))

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

}
