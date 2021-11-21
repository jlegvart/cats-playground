package fs2

import scala.collection.mutable
import cats.effect.IOApp
import java.io.PrintWriter
import java.io.File
import cats.effect.IO
import scala.concurrent.duration._
import fs2.io.file.Path
import fs2.io.file.Files

object ReadWrite extends IOApp.Simple {

  val readPath = "res/shakespeare.txt"
  val writePath = "res/shakespeare_cpy.txt"

  // def run = IO.sleep(5.seconds) >> IO(readWriteFileNonFunctional(readPath, writePath))

  def run = IO.sleep(5.seconds) >> readWriteFileFS2(readPath, writePath).compile.drain.foreverM

  def readWriteFileNonFunctional(readFrom: String, writeTo: String) = {
    val counts = mutable.Map.empty[String, Int]
    val fileSource = scala.io.Source.fromFile(readFrom)
    try fileSource
      .getLines()
      .toList
      .flatMap(_.split("\\W+"))
      .foreach { word =>
        counts += (word -> (counts.getOrElse(word, 0) + 1))
      } finally fileSource.close()
    val fileContent =
      counts.foldLeft("") { case (accumulator, (word, count)) => accumulator + s"$word = $count\n" }
    val writer = new PrintWriter(new File(writeTo))
    writer.write(fileContent)
    writer.close()
  }

  def readWriteFileFS2(
    readFrom: String,
    writeTo: String,
  ) = Files[IO]
    .readAll(Path(readPath))
    .through(text.utf8.decode)
    .through(text.lines)
    .flatMap(line => Stream.apply(line.split("\\W+"): _*))
    .fold(Map.empty[String, Int]) { (count, word) =>
      count + (word -> (count.getOrElse(word, 0) + 1))
    }
    .map(_.foldLeft("") { case (accumulator, (word, count)) => accumulator + s"$word = $count\n" })
    .through(text.utf8.encode)
    .through(Files[IO].writeAll(Path(writePath + ".fs")))

}
