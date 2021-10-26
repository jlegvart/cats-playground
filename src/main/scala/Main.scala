import cats.effect.{IO, IOApp}

import cats.effect.{IO, IOApp}
import scala.concurrent.duration._

import examples.{CountWithSleep}
import examples.FizzBuzz

object Main extends IOApp.Simple {
  
  val run = CountWithSleep.run
  

}
