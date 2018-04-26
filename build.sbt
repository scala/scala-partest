import ScalaModulePlugin._
import VersionKeys._

scalaModuleSettings

name    := "scala-partest"
version := "1.2.0-SNAPSHOT"

scalaVersionsByJvm in ThisBuild := {
  val vs = List("2.13.0-M4-pre-20d3c21")  // new collections

  Map(
    8 -> vs.map(_ -> true),
    9 -> vs.map(_ -> false))
}

scalacOptions += "-Xfatal-warnings"
enableOptimizer

// dependencies
// versions involved in integration builds / that change frequently should be keys, set above!
libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils"      % "1.3.0"
libraryDependencies += "org.scala-sbt"                  % "test-interface" % "1.0"
// mark all Scala dependencies as provided which means one has to explicitly provide them when depending on partest
libraryDependencies += "org.scala-lang"                 % "scalap"         % scalaVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scala-reflect"  % scalaVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scala-compiler" % scalaVersion.value % "provided" intransitive()

OsgiKeys.exportPackage := Seq(s"scala.tools.partest.*;version=${version.value}")
