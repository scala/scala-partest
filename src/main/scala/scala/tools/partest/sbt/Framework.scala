package scala.tools.partest.sbt

import scala.tools.partest.nest.{RunnerSpec, PartestProperties}
import _root_.sbt.testing._
import java.net.URLClassLoader
import java.io.File

object Framework {
  // as partest is not driven by test classes discovered by sbt, need to add this marker fingerprint to definedTests
  val fingerprint = new AnnotatedFingerprint { def isModule = true; def annotationName = "partest" }

  // TODO how can we export `fingerprint` so that a user can just add this to their build.sbt
  // definedTests in Test += new sbt.TestDefinition("partest", fingerprint, true, Array())
}
class Framework extends sbt.testing.Framework {
  def fingerprints: Array[Fingerprint] = Array(Framework.fingerprint)
  def name: String = "partest"

  def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
    new PartestRunner(args, remoteArgs, testClassLoader)
}

// We don't use sbt's Runner or Task abstractions properly.
// sbt runs a single dummy fingerprint match and partest does everything else internally.
case class PartestRunner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader) extends Runner {
  def tasks(taskDefs: Array[TaskDef]): Array[Task] = taskDefs map (PartestTask(_, args): Task)
  def done(): String = "" // no summary
}

/** Run partest in this VM. Assumes we're running in a forked VM!
  */
case class PartestTask(taskDef: TaskDef, args: Array[String]) extends Task {
  /** Executes this task, possibly returning to the client new tasks to execute. */
  def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val forkedCp    = scala.util.Properties.javaClassPath
    val classLoader = new URLClassLoader(forkedCp.split(java.io.File.pathSeparator).map(new File(_).toURI.toURL))
    val config      = RunnerSpec(new PartestProperties(args) {
      // Enable colors if there's an explicit override or all loggers support them
      override def colors = {
        val ptOverride = prop("partest.colors").map(_.toBoolean)
        ptOverride.getOrElse {
          val sbtOverride1 = sys.props.get("sbt.log.format").map(_.toBoolean)
          val sbtOverride2 = sys.props.get("sbt.log.noformat").map(s => !s.toBoolean)
          sbtOverride1.orElse(sbtOverride2).getOrElse {
            loggers.forall(_.ansiCodesSupported())
          }
        }
      }
    })

    if (Runtime.getRuntime().maxMemory() / (1024*1024) < 800)
      loggers foreach (_.warn(s"""Low heap size detected (~ ${Runtime.getRuntime().maxMemory() / (1024*1024)}M). Please add the following to your build.sbt: javaOptions in Test += "-Xmx1G""""))

    val runner = new SBTRunner(config, taskDef.fingerprint(), eventHandler, classLoader)
    try runner.run
    catch {
      case ex: ClassNotFoundException =>
        loggers foreach { l => l.error("Please make sure partest is running in a forked VM by including the following line in build.sbt:\nfork in Test := true") }
        throw ex
    }

    Array()
  }

  /** A possibly zero-length array of string tags associated with this task. */
  def tags: Array[String] = Array()
}
