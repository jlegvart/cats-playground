package producerconsumer

import cats.effect.Async
import cats.effect.IO
import cats.effect.Deferred
import cats.effect.Ref
import cats.effect.MonadCancel
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.effect.std.Console
import cats.effect.ExitCode

import scala.collection.immutable.Queue
import cats.effect.IOApp
import scala.concurrent.duration._
import java.time.OffsetDateTime

object BoundedQueueProducerConsumerCancelable extends IOApp {

  case class State[F[_], A](
    queue: Queue[A],
    capacity: Int,
    takers: Queue[Deferred[F, A]],
    offerers: Queue[(A, Deferred[F, Unit])],
  )

  object State {

    def empty[F[_], A](
      capacity: Int
    ): State[F, A] = State(Queue.empty, capacity, Queue.empty, Queue.empty)

  }

  def producer[F[_]: Async: Console](
    id: Int,
    counterR: Ref[F, Int],
    stateR: Ref[F, State[F, Int]],
  ): F[Unit] = {

    def offer(i: Int): F[Unit] = Deferred[F, Unit].flatMap[Unit] { offerer =>
      Async[F].uncancelable {
        poll => // `poll` used to embed cancelable code, i.e. the call to `offerer.get`
          stateR.modify {
            case State(queue, capacity, takers, offerers) if takers.nonEmpty =>
              val (taker, rest) = takers.dequeue
              State(queue, capacity, rest, offerers) -> taker.complete(i).void
            case State(queue, capacity, takers, offerers) if queue.size < capacity =>
              State(queue.enqueue(i), capacity, takers, offerers) -> Async[F].unit
            case State(queue, capacity, takers, offerers) =>
              val cleanup = stateR.update { s =>
                s.copy(offerers = s.offerers.filter(_._2 ne offerer))
              }
              State(queue, capacity, takers, offerers.enqueue(i -> offerer)) -> poll(offerer.get)
                .onCancel(Console[F].println(s"Cleaning up producer $id") *> cleanup)
          }.flatten
      }
    }

    for {
      i <- counterR.getAndUpdate(_ + 1)
      _ <- offer(i)
      _ <-
        if (i % 10000 == 0)
          Console[F].println(s"Producer $id has reached $i items")
        else
          Async[F].unit
      _ <- producer(id, counterR, stateR)
    } yield ()
  }

  def consumer[F[_]: Async: Console](id: Int, stateR: Ref[F, State[F, Int]]): F[Unit] = {

    val take: F[Int] = Deferred[F, Int].flatMap { taker =>
      Async[F].uncancelable { poll =>
        stateR.modify {
          case State(queue, capacity, takers, offerers) if queue.nonEmpty && offerers.isEmpty =>
            val (i, rest) = queue.dequeue
            State(rest, capacity, takers, offerers) -> Async[F].pure(i)
          case State(queue, capacity, takers, offerers) if queue.nonEmpty =>
            val (i, rest) = queue.dequeue
            val ((move, release), tail) = offerers.dequeue
            State(rest.enqueue(move), capacity, takers, tail) -> release.complete(()).as(i)
          case State(queue, capacity, takers, offerers) if offerers.nonEmpty =>
            val ((i, release), rest) = offerers.dequeue
            State(queue, capacity, takers, rest) -> release.complete(()).as(i)
          case State(queue, capacity, takers, offerers) =>
            val cleanup = stateR.update(s => s.copy(takers = s.takers.filter(_ ne taker)))
            State(queue, capacity, takers.enqueue(taker), offerers) -> poll(taker.get).onCancel(
              Console[F].println(s"Cleaning up consumer $id") *> cleanup
            )
        }.flatten
      }
    }

    for {
      i <- take
      _ <-
        if (i % 10000 == 0)
          Console[F].println(s"Consumer $id has reached $i items")
        else
          Async[F].unit
      _ <- consumer(id, stateR)
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      stateR <- Ref.of[IO, State[IO, Int]](State.empty[IO, Int](capacity = 100))
      counterR <- Ref.of[IO, Int](1)
      producers = List.range(1, 2).map(producer(_, counterR, stateR))
      consumers = List.range(1, 2).map(consumer(_, stateR))

      startTime = OffsetDateTime.now()

      _ <- (producers ++ consumers).parSequence.background.void.use(_ => poll[IO](startTime, 10))
    } yield ExitCode.Success

  def poll[F[_]: Async: Console](startTime: OffsetDateTime, run: Int): F[Unit] =
    for {
      current <- Async[F].delay(OffsetDateTime.now())
      _ <-
        if (current.toEpochSecond() > startTime.plusSeconds(run).toEpochSecond())
          Async[F].unit
        else
          Console[F].println("Sleeping") *> Async[F].sleep(1.second) *> poll(startTime, run)
    } yield ()

}
