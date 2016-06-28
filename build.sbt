import VersionKeys._

scalaModuleSettings

name                       := "scala-partest"

version                    := "1.0.13-SNAPSHOT"

scalaVersion               := crossScalaVersions.value.head

crossScalaVersions         := {
  val java = System.getProperty("java.version")
  if (java.startsWith("1.6."))
    Seq("2.11.7", "2.12.0-M1")
  else if (java.startsWith("1.8."))
    Seq("2.12.0-M4")
  else
    sys.error(s"don't know what Scala versions to build on $java")
}

scalaXmlVersion            := {
  if(scalaVersion.value.startsWith("2.11.") || scalaVersion.value == "2.12.0-M2") "1.0.4" else "1.0.5"
}

// TODO: eliminate "-deprecation:false" for nightlies,
//   included by default because we don't want to break scala/scala pr validation
scalacOptions ++=
  Seq("-feature", "-deprecation:false", "-unchecked", "-Xlint", "-Xfatal-warnings") ++
  (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, scalaMajor)) if scalaMajor < 12 =>
      Seq("-optimize")
    case _ =>
      Seq()  // maybe "-Yopt:l:classpath" eventually?
  })

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
