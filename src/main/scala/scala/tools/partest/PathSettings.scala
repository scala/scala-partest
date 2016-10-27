/* NEST (New Scala Test)
 * Copyright 2007-2013 LAMP/EPFL
 */

package scala.tools.partest

/** Get current value for path settings. Default values are read from system properties `partest.srcdir` and `partest.root`. */
private[partest] class PathSettings(val testSourcePath: String, defaultTestRootName: Option[String]) {
  import PathSettings._

  private def isPartestDir(d: Directory) = (d.name == "test") && (d / testSourcePath isDirectory)
  private def findJar(name: String, ds: Directory*): Either[String, SFile] =
    ds.iterator flatMap (_.files) filter (_ hasExtension "jar") find ( _.name startsWith name ) map (Right(_)) getOrElse
      Left(s"'${name}.jar' not found in '${ds map (_.path) mkString ", "}'.")

  // Directory <root>/test
  val testRoot: Directory = (defaultTestRootName map (Directory(_))) getOrElse {
    val cwd = Directory.Current getOrElse sys.error("user.dir property not set")
    val candidates: List[Directory] = (cwd :: cwd.parents) flatMap (d => List(d, Directory(d / "test")))
    candidates find isPartestDir getOrElse sys.error("Directory 'test' not found.")
  }

  // Directory <root>/test/files or .../scaladoc
  val srcDir = Directory(testRoot / testSourcePath toCanonical)

  def srcSpecLib = findJar("instrumented", Directory(srcDir / "speclib"))
  def srcCodeLib = findJar("code",  Directory(srcDir / "codelib"), Directory(testRoot / "files" / "codelib") /* work with --srcpath pending */)

  private val standardTests: List[Path]  = standardKinds flatMap testsFor

  def testsFor(kind: String): List[Path] =
    (srcDir / kind toDirectory).list.toList filter denotesTestPath

  def grepFor(expr: String): List[Path]  = standardTests filter (t => pathMatchesExpr(t, expr))
  def failedTests: List[Path]            = standardTests filter (p => logOf(p).isFile)

  def denotesTestPath(p: Path) = {
    def denotesTestFile(p: Path) = p.isFile && p.hasExtension("scala", "res", "xml")
    def denotesTestDir(p: Path) = kindOf(p) match {
      case "res"  => false
      case _      => p.isDirectory && p.extension == ""
    }
    denotesTestDir(p) || denotesTestFile(p)
  }

  // true if a test path matches the --grep expression.
  private def pathMatchesExpr(path: Path, expr: String) = {
    // Matches the expression if any source file contains the expr,
    // or if the checkfile contains it, or if the filename contains
    // it (the last is case-insensitive.)
    def matches(p: Path) = (
      (p.path.toLowerCase contains expr.toLowerCase)
        || (p.fileContents contains expr)
      )
    def candidates = {
      (path changeExtension "check") +: {
        if (path.isFile) List(path)
        else path.toDirectory.deepList() filter (_.isJavaOrScala) toList
      }
    }

    (candidates exists matches)
  }
}

private[partest] object PathSettings {
  val standardKinds = ("pos neg run jvm res scalacheck scalap specialized instrumented presentation" split "\\s+").toList

  def kindOf(p: Path) =
    p.toAbsolute.segments takeRight 2 head

  private def logOf(p: Path) =
    p.parent / s"${p.stripExtension}-${kindOf(p)}.log"
}
