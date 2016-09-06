/*                     __                                               *\
**     ________ ___   / /  ___     Scala Parallel Testing               **
**    / __/ __// _ | / /  / _ |    (c) 2007-2013, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala.tools.partest.sbt

import java.net.URLClassLoader

import _root_.sbt.testing._

import scala.tools.partest.TestState._
import scala.tools.partest._
import scala.tools.partest.nest.{SuiteRunner, FileManager, RunnerSpec}

class SBTRunner(config: RunnerSpec.Config, partestFingerprint: Fingerprint, eventHandler: EventHandler,
                testClassLoader: URLClassLoader)
  extends SuiteRunner(config, new FileManager(testClassLoader = testClassLoader)) {

  // no summary, SBT will do that for us
  override protected val printSummary = false
  override protected val partestCmd   = "partest"

  override def onFinishTest(testFile: File, result: TestState): TestState = {
    eventHandler.handle(new Event {
      def fullyQualifiedName: String = testFile.testIdent
      def fingerprint: Fingerprint = partestFingerprint
      def selector: Selector = new TestSelector(testFile.testIdent)
      val (status, throwable) = makeStatus(result)
      def duration: Long = -1
    })
    result
  }

  def makeStatus(t: TestState): (Status, OptionalThrowable) = t match {
    case Uninitialized(_) => (Status.Pending, new OptionalThrowable)
    case Pass(_)          => (Status.Success, new OptionalThrowable)
    case Updated(_)       => (Status.Success, new OptionalThrowable)
    case Skip(_, _)       => (Status.Skipped, new OptionalThrowable)
    case Fail(_, _, _)    => (Status.Failure, new OptionalThrowable)
    case Crash(_, e, _)   => (Status.Error, new OptionalThrowable(e))
  }
}
