package examples

import cats.effect.{IO, IOApp}

import cats.effect.{IO, IOApp}
import scala.concurrent.duration._

object CountWithSleep {

  val a = for {
    num <- IO.ref(0)
    sleep = IO.println("Sleep") *> IO.sleep(1.second)

    next = sleep *> num.get
    printNext = next.flatMap(IO.println(_))

    _ <- (printNext *> num.update(_ + 1)).foreverM.void
  } yield ()

  val run = a
}
