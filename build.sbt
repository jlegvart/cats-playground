scalaVersion := "2.13.6"

name := "cats-playground"
organization := "com.playground"
version := "1.0"

fork in run := true

libraryDependencies += "org.typelevel" %% "cats-core" % "2.3.0" withSources() withJavadoc()
libraryDependencies += "org.typelevel" %% "cats-effect" % "3.2.9" withSources() withJavadoc()

libraryDependencies += "co.fs2" %% "fs2-core" % "3.2.0"
libraryDependencies += "co.fs2" %% "fs2-io" % "3.2.0"