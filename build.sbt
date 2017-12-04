import ScalaModulePlugin._
import VersionKeys._

scalaModuleSettings

name    := "scala-partest"
version := "1.1.3-SNAPSHOT"

scalaVersionsByJvm in ThisBuild := {
  val vs = List("2.12.2", "2.13.0-M1")

  Map(
    8 -> vs.map(_ -> true),
    9 -> vs.map(_ -> false))
}

scalaXmlVersion := "1.0.6"

scalacOptions += "-Xfatal-warnings"
scalacOptions ++= "-Xelide-below" :: "0" :: "-Ywarn-unused" :: Nil
enableOptimizer

// dependencies
// versions involved in integration builds / that change frequently should be keys, set above!
libraryDependencies += "org.apache.ant"                 % "ant"            % "1.8.4" % "provided"
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
