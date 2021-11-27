package twitter

import org.http4s.Request
import org.http4s.client.oauth1
import org.http4s.blaze.client._

import cats.syntax.all._
import cats.effect._
import cats.effect.syntax._
import org.http4s.client.Client
import org.http4s.Method
import org.http4s.Uri
import org.typelevel.jawn.fs2._
import scala.concurrent.ExecutionContext.global
import io.circe.Json
import fs2.text
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger
import config.TwitterConfig

object TWStream {

  def apply[F[_]: Async: std.Console](url: Uri, client: Client[F]) = new TWStream[F](url, client)

}

class TWStream[F[_]: Async: std.Console](url: Uri, client: Client[F]) {

  implicit def unsafeLogger[F[_]: Async] = Slf4jLogger.getLogger[F]

  implicit val f = new io.circe.jawn.CirceSupportParser(None, false).facade

  def sign(
    consumerKey: String,
    consumerSecret: String,
    accessToken: String,
    accessSecret: String,
  )(
    req: Request[F]
  ): F[Request[F]] = {
    val consumer = oauth1.Consumer(consumerKey, consumerSecret)
    val token = oauth1.Token(accessToken, accessSecret)
    oauth1.signRequest(req, consumer, callback = None, verifier = None, token = Some(token))
  }

  def stream(config: TwitterConfig) = {
    val request = Request[F](method = Method.GET, uri = url)
    jsonStream(request, config)
      .map(_.spaces2)
      .evalMap(Logger[F].info(_))
  }

  def jsonStream(req: Request[F], config: TwitterConfig): fs2.Stream[F, Json] =
    for {
      signed <- fs2
        .Stream
        .eval(sign(config.key, config.secret, config.accessToken, config.tokenSecret)(req))
      stream <- client.stream(signed).flatMap(_.body.chunks.parseJsonStream)
    } yield stream

}
