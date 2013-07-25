// import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
// import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact

// previousArtifact := Some("org.scala-lang" % "partest_2.11.0-M4" % "1.0")

organization := "org.scala-lang"

name := "scala-partest"

version := "1.0-RC1"

scalaBinaryVersion := "2.11.0-M4"

scalaVersion := "2.11.0-M4"

libraryDependencies += "org.apache.ant"                 % "ant"            % "1.8.4"

libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils"      % "1.3.0"

// libraryDependencies += "org.scala-tools.testing"        % "test-interface" % "0.5"

libraryDependencies += "org.scala-lang"                 % "scala-xml"      % "2.11.0-M4"

libraryDependencies += "org.scala-lang"                 % "scalap"         % "2.11.0-M4"

libraryDependencies += "org.scalacheck"                %% "scalacheck"     % "1.10.2-SNAPSHOT"

libraryDependencies += "org.scala-sbt"                  % "test-interface" % "1.0"

resourceGenerators in Compile <+= Def.task {
  val props = new java.util.Properties
  props.put("version.number", version.value)
  val file = (resourceManaged in Compile).value / "partest.properties"
  IO.write(props, null, file)
  Seq(file)
}

mappings in (Compile, packageBin) += {
   (baseDirectory.value / "partest.properties") -> "partest.properties"
}