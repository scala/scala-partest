import DependencyKeys.scalaXmlVersion

organization := "org.scala-lang.modules"

name := "scala-partest"

version := "1.0.0-SNAPSHOT"

// TODO: bump to 1.0.0-RC5 as soon as it's out
scalaXmlVersion := "1.0.0-RC6"

// so we don't have to wait for sonatype to synch to maven central when deploying a new module
resolvers += Resolver.sonatypeRepo("releases")

// to allow compiling against snapshot versions of Scala
resolvers += Resolver.sonatypeRepo("snapshots")

// dependencies:
libraryDependencies += "org.apache.ant"                 % "ant"            % "1.8.4"

libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils"      % "1.3.0"

libraryDependencies += "org.scala-sbt"                  % "test-interface" % "1.0"

// mark as intransitive because 1.10.1 released against Scala 2.11.0-M6 has wrong dependencies
// once we upgrade to M7 the intransitive bit can be dropped
// however, provided should stay; if one wants to run scalacheck tests it should depend on
// scalacheck explicitly
libraryDependencies += "org.scalacheck"                %% "scalacheck"     % "1.10.1" % "provided" intransitive()

// mark all scala dependencies as provided which means one has to explicitly provide them when depending on partest
// this allows for easy testing of modules (like scala-xml) that provide tested classes themselves and shouldn't
// pull in an older version of itself
libraryDependencies += "org.scala-lang.modules"        %% "scala-xml"      % scalaXmlVersion.value % "provided" intransitive()

libraryDependencies += "org.scala-lang"                 % "scalap"         % scalaVersion.value % "provided" intransitive()

libraryDependencies += "org.scala-lang"                 % "scala-reflect"  % scalaVersion.value % "provided" intransitive()

libraryDependencies += "org.scala-lang"                 % "scala-compiler" % scalaVersion.value % "provided" intransitive()

// standard stuff follows:
scalaVersion := "2.11.0-M6"

// NOTE: not necessarily equal to scalaVersion
// (e.g., during PR validation, we override scalaVersion to validate,
// but don't rebuild scalacheck, so we don't want to rewire that dependency)
scalaBinaryVersion := "2.11.0-M6"

// don't use for doc scope, scaladoc warnings are not to be reckoned with
scalacOptions in (Compile, compile) ++= Seq("-optimize", "-Xfatal-warnings", "-feature", "-deprecation", "-unchecked", "-Xlint")


// Generate $name.properties to store our version as well as the scala version used to build
// TODO: why doesn't this work for scala-partest.properties?? (After updating def propCategory in Properties, of course)
resourceGenerators in Compile <+= Def.task {
  val props = new java.util.Properties
  props.put("version.number", version.value)
  props.put("scala.version.number", scalaVersion.value)
  props.put("scala.binary.version.number", scalaBinaryVersion.value)
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
        <distribution>repo</distribution>
        <name>BSD 3-Clause</name>
        <url>https://github.com/scala/{name.value}/blob/master/LICENSE.md</url>
    </license>
   </licenses>
  <scm>
    <connection>scm:git:git://github.com/scala/{name.value}.git</connection>
    <url>https://github.com/scala/{name.value}</url>
  </scm>
  <issueManagement>
    <system>JIRA</system>
    <url>https://issues.scala-lang.org/</url>
  </issueManagement>
  <developers>
    <developer>
      <id>epfl</id>
      <name>EPFL</name>
    </developer>
    <developer>
      <id>Typesafe</id>
      <name>Typesafe, Inc.</name>
    </developer>
  </developers>
)

