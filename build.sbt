scalaVersion := "2.13.6"

name := "cats-playground"
organization := "com.playground"
version := "1.0"

fork in run := true

val catsVersion = "2.3.0"
val catsEffectVersion = "3.2.9"
val fs2Version = "3.2.0"
val http4sVersion = "0.23.6"

libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion withSources() withJavadoc()
libraryDependencies += "org.typelevel" %% "cats-effect" % catsEffectVersion withSources() withJavadoc()

libraryDependencies += "co.fs2" %% "fs2-core" % fs2Version withSources() withJavadoc()
//libraryDependencies += "co.fs2" %% "fs2-io" % fs2Version withSources() withJavadoc()

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)

// Jsoup
libraryDependencies += "org.jsoup" % "jsoup" % "1.14.3"