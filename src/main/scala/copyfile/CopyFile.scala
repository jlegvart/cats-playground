package copyfile

import cats.syntax.all._
import scala.concurrent.duration._
import cats.effect.IOApp
import cats.effect._

import java.io._
import java.nio.file.Files
import java.nio.file.Paths

import cats.effect.std

object CopyFile extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <-
        if (args.length < 2)
          IO.raiseError(
            new IllegalArgumentException("Need origin and destination files")
          )
        else
          IO.unit

      in = args(0)
      out = args(1)

      orig = new File(args(0))
      dest = new File(args(1))

      a <- checkIdentical[IO](in, out)
      destOk <- checkDestination[IO](dest)
      _ <-
        if (destOk)
          runCopy(orig, dest, 1024 * 10)
        else
          IO.println("Terminated")

    } yield ExitCode.Success).handleErrorWith(e =>
      IO.println(s"Error during execution: ${e.getMessage()}") *> IO.pure(
        ExitCode.Error
      )
    )

  def runCopy(origin: File, destination: File, bufferSize: Int) =
    for {
      count <- CopyFile.copy[IO](origin, destination, bufferSize)
      _ <- IO.println(
        s"$count bytes copied from ${origin.getPath} to ${destination.getPath}"
      )
    } yield ()

  def checkIdentical[F[_]: Sync](in: String, out: String): F[Unit] =
    if (in == out) {
      Sync[F].raiseError(
        new RuntimeException("Origin and destination are the same")
      )
    } else {
      Sync[F].unit
    }

  def checkDestination[F[_]: Sync: std.Console](dest: File) =
    if (dest.isFile()) {
      for {
        _ <- std.Console[F].println("Destination exists, overwrite y/n?")
        in <- std.Console[F].readLine
      } yield (in == "y")
    } else {
      Sync[F].pure(true)
    }

  def copy[F[_]: Async](
    origin: File,
    destination: File,
    bufferSize: Int,
  ): F[Long] = inputOutputStreams(origin, destination).use { case (in, out) =>
    transfer(in, out, bufferSize)
  }

  def transfer[F[_]: Async](
    origin: InputStream,
    destination: OutputStream,
    bufferSize: Int,
  ): F[Long] = transmit(origin, destination, new Array[Byte](bufferSize), 0L)

  def transmit[F[_]: Async](
    origin: InputStream,
    destination: OutputStream,
    buffer: Array[Byte],
    acc: Long,
  ): F[Long] =
    for {
      //!!!!!!!!!!!!
      // Introduced artificial delay when reading a file
      //!!!!!!!!!!!!
      amount <-
        Async[F].sleep(10.millisecond) *> Async[F].blocking(
          origin.read(buffer, 0, buffer.length)
        )
      count <-
        if (amount > -1)
          Async[F].blocking(destination.write(buffer, 0, amount)) >> transmit(
            origin,
            destination,
            buffer,
            acc + amount,
          )
        else
          Async[F].pure(
            acc
          ) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield count // Returns the actual amount of bytes transmitted

  def inputStream[F[_]: Async](f: File): Resource[F, FileInputStream] =
    Resource.make {
      Async[F].delay(println("Acquiring input stream")) *>
        Async[F]
          .blocking(new FileInputStream(f))
          .handleErrorWith(throwable =>
            throwable match {
              case e: SecurityException =>
                Async[F].raiseError(new RuntimeException("Error reading file"))
            }
          )
    } { inStream =>
      Async[F].delay(println("Releasing input stream")) *>
        Async[F].blocking(inStream.close()).handleErrorWith(_ => Async[F].unit)
    }

  def outputStream[F[_]: Async](f: File): Resource[F, FileOutputStream] =
    Resource.make {
      Async[F].delay(println("Acquiring output stream")) *> Async[F].blocking(
        new FileOutputStream(f)
      )
    } { outStream =>
      Async[F].delay(println("Releasing output stream")) *> Async[F]
        .blocking(outStream.close())
        .handleErrorWith(_ => Async[F].unit)
    }

  def inputOutputStreams[F[_]: Async](
    in: File,
    out: File,
  ): Resource[F, (InputStream, OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)

}
