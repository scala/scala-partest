/* NEST (New Scala Test)
 * Copyright 2007-2013 LAMP/EPFL
 * @author Philipp Haller
 */

package scala.tools.partest
package nest

object ConsoleRunner {
  def main(args: Array[String]): Unit = {
    val r = new SuiteRunner(
      RunnerSpec(new PartestProperties(args)),
      // the script sets up our classpath for us via sbt
      new FileManager(ClassPath split PathResolver.Environment.javaUserClassPath map (Path(_)))
    )
    // So we can ctrl-C a test run and still hear all
    // the buffered failure info.
    scala.sys addShutdownHook r.issueSummaryReport()
    System.exit( if (r.run) 0 else 1 )
  }
}
