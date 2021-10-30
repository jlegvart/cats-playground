package copyfile

import cats.effect._
import cats.syntax.all._
import scala.concurrent.duration._

import java.io._

object CopyFilePolymorphic extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <-
        if (args.length < 2)
          IO.raiseError(
            new IllegalArgumentException("Need origin and destination files")
          )
        else
          IO.unit
      orig = new File(args.head)
      dest = new File(args.tail.head)
      count <- copy[IO](orig, dest)
      _ <- IO.println(
        s"$count bytes copied from ${orig.getPath} to ${dest.getPath}"
      )
    } yield ExitCode.Success

  def copy[F[_]: Async: std.Console](
    origin: File,
    destination: File,
  ): F[Long] = inputOutputStreams(origin, destination).use { case (in, out) =>
    transfer(in, out)
  }

  def transmit[F[_]: Async](
    origin: InputStream,
    destination: OutputStream,
    buffer: Array[Byte],
    acc: Long,
  ): F[Long] =
    for {
      amount <-
        Async[F].sleep(10.milliseconds) *> Sync[F].blocking(
          origin.read(buffer, 0, buffer.length)
        )
      count <-
        if (amount > -1)
          Sync[F].blocking(destination.write(buffer, 0, amount)) >> transmit(
            origin,
            destination,
            buffer,
            acc + amount,
          )
        else
          Sync[F].pure(
            acc
          ) // End of read stream reached (by java.io.InputStream contract), nothing to write
    } yield count // Returns the actual amount of bytes transmitted

  def transfer[F[_]: Async](
    origin: InputStream,
    destination: OutputStream,
  ): F[Long] = transmit(origin, destination, new Array[Byte](1024 * 10), 0L)

  def inputStream[F[_]: Sync: std.Console](
    f: File
  ): Resource[F, FileInputStream] =
    Resource.make {
      std.Console[F].println("Acquiring input stream") *> Sync[F].blocking(
        new FileInputStream(f)
      )
    } { inStream =>
      std.Console[F].println("Releasing input stream") *>
        Sync[F].blocking(inStream.close()).handleErrorWith(_ => Sync[F].unit)
    }

  def outputStream[F[_]: Sync: std.Console](
    f: File
  ): Resource[F, FileOutputStream] =
    Resource.make {
      std.Console[F].println("Acquiring output stream") *>
        Sync[F].blocking(new FileOutputStream(f))
    } { outStream =>
      std.Console[F].println("Releasing output stream") *>
        Sync[F].blocking(outStream.close()).handleErrorWith(_ => Sync[F].unit)
    }

  def inputOutputStreams[F[_]: Sync: std.Console](
    in: File,
    out: File,
  ): Resource[F, (InputStream, OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream, outStream)

}
