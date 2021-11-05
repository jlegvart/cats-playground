package producerconsumer

import cats.effect.Deferred
import cats.effect.Ref
import cats.effect.Async
import cats.effect.std.Console
import cats.syntax.all._
import cats.effect.syntax.all._

import scala.collection.immutable.Queue

private final case class State[F[_], A](
  queue: Queue[A],
  capacity: Int,
  takers: Queue[Deferred[F, A]],
  offerers: Queue[(A, Deferred[F, Unit])],
)

private object State {

  def empty[F[_], A](
    capacity: Int
  ): State[F, A] = State(Queue.empty, capacity, Queue.empty, Queue.empty)

}

object ConcurrentQueue {

  def apply[F[_]: Async: Console, A](capacity: Int): F[ConcurrentQueue[F, A]] = Ref
    .of[F, State[F, A]](State.empty[F, A](capacity))
    .map(state => new ConcurrentQueue[F, A](state))

}

class ConcurrentQueue[F[_]: Async: Console, A](stateR: Ref[F, State[F, A]]) {

  def offer(i: A): F[Unit] = Deferred[F, Unit].flatMap[Unit] { offerer =>
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
              .onCancel(cleanup)
        }.flatten
    }
  }

  def take(): F[A] = Deferred[F, A].flatMap { taker =>
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
            cleanup
          )
      }.flatten
    }
  }

}
