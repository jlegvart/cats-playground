package count

import cats.effect.IO
import cats.effect.IOApp
import scala.concurrent.duration._

object CountWithSleep extends IOApp.Simple {

  val run =
    for {
      num <- IO.ref(0)
      sleep = IO.println("Sleep") *> IO.sleep(1.second)

      next = sleep *> num.get

      _ <- (next.flatMap(IO.println(_)) *> num.update(_ + 1)).foreverM.void
    } yield ()

}
