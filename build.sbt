import ScalaModulePlugin._
import VersionKeys._

scalaModuleSettings

name    := "scala-partest"
version := "1.1.4-SNAPSHOT"

scalaVersionsByJvm in ThisBuild := {
  val vs = List("2.12.7")

  Map(
    8 -> vs.map(_ -> true),
    9 -> vs.map(_ -> false),
    10 -> vs.map(_ -> false),
    11 -> vs.map(_ -> false))
}

scalaXmlVersion := "1.0.6"

scalacOptions += "-Xfatal-warnings"
enableOptimizer

// dependencies
// versions involved in integration builds / that change frequently should be keys, set above!
libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils"      % "1.3.0"
libraryDependencies += "org.scala-sbt"                  % "test-interface" % "1.0"
// mark all scala dependencies as provided which means one has to explicitly provide them when depending on partest
// this allows for easy testing of modules (like scala-xml) that provide tested classes themselves and shouldn't
// pull in an older version of itself
libraryDependencies += "org.scala-lang.modules"        %% "scala-xml"      % scalaXmlVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scalap"         % scalaVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scala-reflect"  % scalaVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scala-compiler" % scalaVersion.value % "provided" intransitive()

OsgiKeys.exportPackage := Seq(s"scala.tools.partest.*;version=${version.value}")
