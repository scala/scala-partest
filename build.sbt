import ScalaModulePlugin._
import VersionKeys._

scalaModuleSettings

name    := "scala-partest"
version := "1.0.19-SNAPSHOT"

scalaVersionsByJvm in ThisBuild := {
  val v211 = "2.11.12"

  Map(
    6 -> List(v211 -> true),
    7 -> List(v211 -> false),
    8 -> List(v211 -> false),
    11 -> List(v211 -> false),
    12 -> List(v211 -> false))
}

scalaXmlVersion := {
  if (scalaVersion.value.startsWith("2.11.")) "1.0.4" else "1.0.6"
}

scalaCheckVersion := "1.11.6"

scalacOptions += "-Xfatal-warnings"
enableOptimizer

// dependencies
// versions involved in integration builds / that change frequently should be keys, set above!
libraryDependencies += "org.apache.ant"                 % "ant"            % "1.8.4" % "provided"
libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils"      % "1.3.0"
libraryDependencies += "org.scala-sbt"                  % "test-interface" % "1.0"
// to run scalacheck tests, depend on scalacheck separately
libraryDependencies += "org.scalacheck"                %% "scalacheck"     % scalaCheckVersion.value % "provided"
// mark all scala dependencies as provided which means one has to explicitly provide them when depending on partest
// this allows for easy testing of modules (like scala-xml) that provide tested classes themselves and shouldn't
// pull in an older version of itself
libraryDependencies += "org.scala-lang.modules"        %% "scala-xml"      % scalaXmlVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scalap"         % scalaVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scala-reflect"  % scalaVersion.value % "provided" intransitive()
libraryDependencies += "org.scala-lang"                 % "scala-compiler" % scalaVersion.value % "provided" intransitive()

OsgiKeys.exportPackage := Seq(s"scala.tools.partest.*;version=${version.value}")
