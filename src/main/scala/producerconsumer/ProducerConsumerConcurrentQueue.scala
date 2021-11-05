package producerconsumer

import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all._
import cats.effect.syntax
import cats.effect.std._
import cats.effect.Async

object ProducerConsumerConcurrentQueue extends IOApp {

  def producer[F[_]: Async: Console](
    id: Int,
    counterR: Ref[F, Int],
    queue: ConcurrentQueue[F, Int],
  ): F[Unit] =
    for {
      i <- counterR.getAndUpdate(_ + 1)
      _ <- queue.offer(i)
      _ <-
        if (i % 10000 == 0)
          Console[F].println(s"Producer $id has reached $i items")
        else
          Async[F].unit
      _ <- producer(id, counterR, queue)
    } yield ()

  def consumer[F[_]: Async: Console](id: Int, queue: ConcurrentQueue[F, Int]): F[Unit] =
    for {
      take <- queue.take()
      _ <-
        if (take % 10000 == 0)
          Console[F].println(s"Consumer $id has reached $take items")
        else
          Async[F].unit
      _ <- consumer(id, queue)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    for {
      counterR <- Ref.of[IO, Int](1)
      queue <- ConcurrentQueue[IO, Int](50)

      producers = List.range(1, 2).map(producer(_, counterR, queue))
      consumers = List.range(1, 10).map(consumer(_, queue))

      result <- (producers ++ consumers).parSequence.as(ExitCode.Success)
    } yield result

}
