import VersionKeys._

scalaModuleSettings

name                       := "scala-partest"

version                    := "1.0.9-SNAPSHOT"

scalaVersion               := crossScalaVersions.value.head

crossScalaVersions         := Seq("2.11.6", "2.12.0-M1")

scalaXmlVersion            := "1.0.4"

scalaCheckVersion          := "1.11.6"

// TODO: enable "-Xfatal-warnings" for nightlies,
// off by default because we don't want to break scala/scala pr validation due to deprecation
// don't use for doc scope, scaladoc warnings are not to be reckoned with
scalacOptions in (Compile, compile) ++= Seq("-optimize", "-feature", "-deprecation", "-unchecked", "-Xlint")

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

mimaPreviousVersion := Some("1.0.5")
