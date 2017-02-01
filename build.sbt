import VersionKeys._

scalaModuleSettings

name                       := "scala-partest"

version                    := "1.1.1-SNAPSHOT"

scalaVersion               := crossScalaVersions.value.head

crossScalaVersions         := Seq("2.12.1")

scalaXmlVersion            := "1.0.6"

// TODO: eliminate "-deprecation:false" for nightlies,
//   included by default because we don't want to break scala/scala pr validation
scalacOptions ++=
  Seq("-feature", "-deprecation:false", "-unchecked", "-Xlint", "-Xfatal-warnings")

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
