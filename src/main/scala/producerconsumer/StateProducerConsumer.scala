package producerconsumer

import cats.effect.Async
import cats.effect.Deferred
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.Sync
import cats.effect.std.Console
import cats.instances.list._
import cats.syntax.all._
import scala.concurrent.duration._

import scala.collection.immutable.Queue

/** Multiple producer - multiple consumer system using an unbounded concurrent queue.
  *
  * Second part of cats-effect tutorial at https://typelevel.org/cats-effect/tutorial/tutorial.html
  *
  * Code to _offer_ and _take_ elements to/from queue is taken from CE3's Queue implementation.
  */
object StateProducerConsumer extends IOApp {

  case class State[F[_], A](queue: Queue[A], takers: Queue[Deferred[F, A]])

  object State {
    def empty[F[_], A]: State[F, A] = State(Queue.empty, Queue.empty)
  }

  def producer[F[_]: Async: Console](
    id: Int,
    counterR: Ref[F, Int],
    stateR: Ref[F, State[F, Int]],
  ): F[Unit] = {

    def offer(i: Int): F[Unit] =
      stateR.modify {
        case State(queue, takers) if takers.nonEmpty =>
          val (taker, rest) = takers.dequeue
          State(queue, rest) -> taker.complete(i).void
        case State(queue, takers) => State(queue.enqueue(i), takers) -> Async[F].unit
      }.flatten

    for {
      i <- Async[F].sleep(5.milliseconds) *> counterR.getAndUpdate(_ + 1)
      _ <- offer(i)
      _ <-
        if (i % 1000 == 0)
          Console[F].println(s"Producer $id has reached $i items")
        else
          Async[F].unit
      _ <- producer(id, counterR, stateR)
    } yield ()
  }

  def consumer[F[_]: Async: Console](id: Int, stateR: Ref[F, State[F, Int]]): F[Unit] = {

    val take: F[Int] = Deferred[F, Int].flatMap { taker =>
      stateR.modify {
        case State(queue, takers) if queue.nonEmpty =>
          val (i, rest) = queue.dequeue
          State(rest, takers) -> Async[F].pure(i)
        case State(queue, takers) => State(queue, takers.enqueue(taker)) -> taker.get
      }.flatten
    }

    for {
      i <- take
      _ <-
        if (i % 1000 == 0)
          Console[F].println(s"Consumer $id has reached $i items")
        else
          Async[F].unit
      _ <- consumer(id, stateR)
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      stateR <- Ref.of[IO, State[IO, Int]](State.empty[IO, Int])
      counterR <- Ref.of[IO, Int](1)
      producers = List.range(1, 10).map(producer(_, counterR, stateR)) // 10 producers
      consumers = List.range(1, 10).map(consumer(_, stateR)) // 10 consumers
      res <- (producers ++ consumers)
        .parSequence
        .as(
          ExitCode.Success
        ) // Run producers and consumers in parallel until done (likely by user cancelling with CTRL-C)
        .handleErrorWith { t =>
          Console[IO].errorln(s"Error caught: ${t.getMessage}").as(ExitCode.Error)
        }
    } yield res

}
