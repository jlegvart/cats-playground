package producerconsumer

import cats.effect.Async
import cats.effect.Deferred
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.std.Console
import cats.implicits._
import scala.concurrent.duration._
import cats.effect.std

object ProducerConsumerWithQueue extends IOApp {

  def producer[F[_]: Async: Console](
    id: Int,
    counterR: Ref[F, Int],
    queue: std.Queue[F, Int],
  ): F[Unit] =
    for {
      i <- counterR.getAndUpdate(_ + 1)
      _ <- Async[F].sleep(10.milliseconds) *> queue.offer(i)
      _ <-
        if (i % 1000 == 0)
          Console[F].println(s"Producer $id has reached $i items")
        else
          Sync[F].unit
      _ <- producer(id, counterR, queue)
    } yield ()

  def consumer[F[_]: Async: Console](id: Int, queue: std.Queue[F, Int]): F[Unit] =
    for {
      i <- Async[F].sleep(10.milliseconds) *> queue.take
      _ <-
        if (i % 1000 == 0)
          Console[F].println(s"Consumer $id has reached $i items")
        else
          Async[F].unit
      _ <- consumer(id, queue)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    for {
      queue <- std.Queue.unbounded[IO, Int]
      counterR <- Ref.of[IO, Int](1)
      producers = List.range(1, 2).map(producer(_, counterR, queue))
      consumers = List.range(1, 2).map(consumer(_, queue))

      p = poll(queue)

      _ <- (producers ++ consumers).parSequence.background.void.use(_ => p)
    } yield ExitCode.Success

  def poll[F[_]: Async: std.Console](queue: std.Queue[F, Int]) =
    queue
      .size
      .flatMap(size => (Async[F].sleep(1.second) *> Console[F].println(s"Current size: $size")))
      .foreverM
      .void

}
