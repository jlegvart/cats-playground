package producerconsumer

import cats.effect._
import fs2._
import fs2.concurrent.Channel
import cats.syntax.all._
import scala.concurrent.duration._

sealed trait Metric
case class BadRecordDiscarded(reason: String) extends Metric

trait Metrics {
  def send(m: Metric): IO[Unit]
}

object Metrics {

  def create: Resource[IO, Metrics] = Resource
    .make(Channel.unbounded[IO, Metric])(_.close.void)
    .flatMap { chan =>
      val consumer = chan.stream.evalMap(m => IO.println(s"received metric $m"))

      consumer.compile.drain.background.as {
        new Metrics {
          def send(m: Metric) = chan.send(m).void
        }
      }
    }

}

object Ex extends IOApp.Simple {

  def run: IO[Unit] = Metrics.create.use { metrics =>
    Stream
      .range(0, 5)
      .covary[IO]
      .metered(1.second) // just for the example
      .evalTap(i => metrics.send(BadRecordDiscarded(i.toString)))
      .compile
      .toList
      .flatMap { elems =>
        IO.println(s"Processed $elems")
      }
  }

}
