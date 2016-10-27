package scala.tools.partest.nest

import language.postfixOps

import java.lang.Runtime
import java.util.Properties
import scala.tools.cmd.{ CommandLine, Interpolation, Meta, Reference, Spec, Instance }
import scala.concurrent.duration.Duration

trait RunnerSpec extends Spec with Meta.StdOpts with Interpolation {
  def referenceSpec       = RunnerSpec
  def programInfo         = Spec.Info(
      "console-runner",
      "Usage: ConsoleRunner [options] [test test ...]",
      "scala.tools.partest.nest.ConsoleRunner")

  heading("Test categories:")
  val optPos          = "pos"          / "run compilation tests (success)"   --?
  val optNeg          = "neg"          / "run compilation tests (failure)"   --?
  val optRun          = "run"          / "run interpreter and backend tests" --?
  val optJvm          = "jvm"          / "run JVM backend tests"             --?
  val optRes          = "res"          / "run resident compiler tests"       --?
  val optScalap       = "scalap"       / "run scalap tests"                  --?
  val optSpecialized  = "specialized"  / "run specialization tests"          --?
  val optScalacheck   = "scalacheck"   / "run ScalaCheck tests"              --?
  val optInstrumented = "instrumented" / "run instrumented tests"            --?
  val optPresentation = "presentation" / "run presentation compiler tests"   --?

  heading("Test runner options:")
  val optFailed       = "failed"       / "run only those tests that failed during the last run"                           --?
  val optTimeout      = "timeout"      / "aborts the test suite after the given amount of time"                           --|
  val optPack         = "pack"         / "pick compiler/reflect/library in build/pack, and run all tests"                 --?
  val optGrep         = "grep"         / "run all tests whose source file contains the expression given to grep"          --|
  val optUpdateCheck  = "update-check" / "instead of failing tests with output change, update checkfile (use with care!)" --?
  val optBuildPath    = "buildpath"    / "set (relative) path to build jars (ex.: --buildpath build/pack)"                --|
  val optClassPath    = "classpath"    / "set (absolute) path to build classes"                                           --|
  val optSourcePath   = "srcpath"      / "set (relative) path to test source files (ex.: --srcpath pending)"              --|

  heading("Test output options:")
  val optShowDiff     = "show-diff"    / "show diffs for failed tests"       --?
  val optShowLog      = "show-log"     / "show log files for failed tests"   --?
  val optVerbose      = "verbose"      / "show verbose progress information" --?
  val optTerse        = "terse"        / "show terse progress information"   --?
  val optDebug        = "debug"        / "enable debugging output"           --?
  
  heading("Other options:")
  val optVersion      = "version"      / "show Scala version and exit"  --?
  val optHelp         = "help"         / "show this page and exit"      --?

}

object RunnerSpec extends RunnerSpec with Reference {
  trait Config extends RunnerSpec with Instance {
    def props: PartestProperties
  }

  type ThisCommandLine = CommandLine
  def creator(args: List[String]): ThisCommandLine = new CommandLine(RunnerSpec, args)

  def apply(pp: PartestProperties): Config =
    new { val parsed = creator(pp.rest) } with Config { val props = pp }
}

/** Various properties used by Partest. They can be set from the command line (required in sbt where that's
  * all we get to pass to Partest) or system properties (usable from the partest shell script). */
class PartestProperties(args: Iterable[String]) {
  private[this] val Def = "-D([^=]*)=(.*)".r
  private[this] val data: Properties = new Properties(System.getProperties)
  protected def prop(key: String): Option[String] = Option(data.getProperty(key))

  args.foreach {
    case Def(k, v) => data.setProperty(k, v)
    case _ =>
  }

  lazy val rest = args.filterNot(_.startsWith("-D")).toList

  def sourcePath  = prop("partest.srcdir")      getOrElse "files"
  def javaCmd     = prop("partest.javacmd")     orElse    jdkexec("java")  getOrElse "java"
  def javacCmd    = prop("partest.javac_cmd")   orElse    jdkexec("javac") getOrElse "javac"
  def javaOpts    = prop("partest.java_opts")   getOrElse  ""     // opts when running java during tests
  def scalacOpts  = prop("partest.scalac_opts") getOrElse  ""
  def gitDiffOpts = prop("partest.git_diff_options") getOrElse  ""
  def debug       = prop("partest.debug")   map (_.toBoolean) getOrElse false
  def colors      = prop("partest.colors")  map (_.toBoolean) getOrElse false
  def testBuild   = prop("partest.build")
  def errorCount  = prop("partest.errors")  map (_.toInt) getOrElse 0
  def numThreads  = prop("partest.threads") map (_.toInt) getOrElse Runtime.getRuntime.availableProcessors
  def waitTime    = Duration(prop("partest.timeout") getOrElse "4 hours")
  def root        = prop("partest.root")

  // probe for the named executable
  private def jdkexec(name: String): Option[String] = {
    import scala.reflect.io.Path, Path._
    import scala.util.Properties.jdkHome
    Some(Path(jdkHome) / "bin") filter (_.isDirectory) flatMap { p =>
      val candidates = (p walkFilter { e => (e.name == name || e.name.startsWith(s"$name.")) && e.jfile.canExecute }).toList
      (candidates find (_.name == name) orElse candidates.headOption) map (_.path)
    }
  }
}
