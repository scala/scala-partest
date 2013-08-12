organization := "org.scala-lang"

name := "scala-partest"

version := "1.0-RC1"

scalaVersion := "2.11.0-M4"

scalaBinaryVersion := scalaVersion.value

libraryDependencies += "org.apache.ant"                 % "ant"            % "1.8.4"

libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils"      % "1.3.0"

libraryDependencies += "org.scala-lang"                 % "scala-xml"      % "2.11.0-M4"

libraryDependencies += "org.scala-lang"                 % "scalap"         % "2.11.0-M4"

libraryDependencies += "org.scalacheck"                %% "scalacheck"     % "1.10.1"

libraryDependencies += "org.scala-sbt"                  % "test-interface" % "1.0"


// partest.properties
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


// maven publishing
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://www.scala-lang.org/</url>
  <inceptionYear>2002</inceptionYear>
  <licenses>
    <license>
      <name>BSD-like</name>
      <url>http://www.scala-lang.org/downloads/license.html
      </url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:git://github.com/scala/scala-partest.git</connection>
    <url>https://github.com/scala/scala-partest</url>
  </scm>
  <issueManagement>
    <system>JIRA</system>
    <url>https://issues.scala-lang.org/</url>
  </issueManagement>
  <developers>
    <developer>
      <id>lamp</id>
      <name>EPFL LAMP</name>
    </developer>
    <developer>
      <id>Typesafe</id>
      <name>Typesafe, Inc.</name>
    </developer>
  </developers>
)

// TODO: mima
// import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
// import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
// previousArtifact := Some("org.scala-lang" % "partest_2.11.0-M4" % "1.0")

