package fs2

import cats.effect.IOApp
import org.http4s.client.blaze._
import org.http4s.client._
import scala.concurrent.ExecutionContext.global
import cats.effect.IO
import cats._
import cats.effect._
import cats.implicits._
import org.http4s.Uri
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.Method._
import org.http4s.Request
import org.http4s.Method
import cats.data.EitherT
import scala.concurrent.duration._

object ReadWriteRemote extends IOApp.Simple {

  val endpoint = "https://ocw.mit.edu/ans7870/6/6.006/s08/lecturenotes/files/t8.shakespeare.txt"

  def run =
    (for {
      u <- Uri.fromString(endpoint)
      stream <- getRemoteResource(u)
    } yield stream) match {
      case Right(stream) => stream.compile.drain
      case Left(error)   => IO.raiseError(new RuntimeException(error.message))
    }

  def getRemoteResource(uri: Uri) =
    (for {
      client <- initClient
      req = Request[IO](Method.GET, uri)
      chunk <- client.stream(req).flatMap(_.body.chunks)
      _ <- processChunk(chunk)
    } yield ()).asRight

  def initClient() = BlazeClientBuilder[IO](global).stream

  def processChunk(chunk: Chunk[Byte]) = Stream
    .chunk(chunk)
    .through(text.utf8.decode)
    .through(text.lines)
    .map(println(_))

}
