package fs2

import cats.effect.IO
import cats.effect.IOApp
import fs2.Stream
import fs2.text
import fs2.io.file.Files
import fs2.io.file.Path

object FSExample extends IOApp.Simple {

  val converter = {
    def fahrenheitToCelsius(f: Double): Double = (f - 32.0) * (5.0 / 9.0)

    def round(d: Double): Double = d.round

    Files[IO]
      .readAll(Path("res/fahrenheit.txt"))
      .through(text.utf8.decode)
      .through(text.lines)
      .filter(s => !s.trim.isEmpty && !s.startsWith("//"))
      .map(line => fahrenheitToCelsius(line.toDouble))
      .map(round(_))
      .map(_.toString())
      .intersperse("\n")
      .through(text.utf8.encode)
      .through(text.utf8.decode)

  }

  def run: IO[Unit] =
    for {
      list <- converter.compile.toList
      _ <- IO.println(list)
    } yield ()

}
