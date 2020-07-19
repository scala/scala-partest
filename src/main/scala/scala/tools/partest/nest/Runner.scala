/* NEST (New Scala Test)
 * Copyright 2007-2013 LAMP/EPFL
 * @author Paul Phillips
 */
package scala.tools.partest
package nest

import java.io.{Console => _, _}
import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset
import java.nio.file.{Files, StandardOpenOption}
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.NANOSECONDS

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.reflect.internal.FatalError
import scala.reflect.internal.util.ScalaClassLoader
import scala.sys.process.{Process, ProcessLogger}
import scala.tools.nsc.Properties.{isWin, javaHome, javaVmInfo, javaVmName, javaVmVersion, propOrEmpty, versionMsg}
import scala.tools.nsc.{CompilerCommand, Global, Settings}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.util.stackTraceString
import scala.util.{Failure, Success, Try}
import ClassPath.join
import TestState.{Crash, Fail, Pass, Uninitialized, Updated}
import FileManager.{compareContents, joinPaths, withTempFile}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.control.ControlThrowable

trait TestInfo {
  /** pos/t1234 */
  def testIdent: String

  /** pos */
  def kind: String

  // inputs

  /** pos/t1234.scala or pos/t1234 if dir */
  def testFile: File

  /** pos/t1234.check */
  def checkFile: File

  /** pos/t1234.flags */
  def flagsFile: File

  // outputs

  /** pos/t1234-pos.obj */
  def outFile: File

  /** pos/t1234-pos.log */
  def logFile: File
}

/** Run a single test. Rubber meets road. */
class Runner(val testFile: File, val suiteRunner: SuiteRunner, val nestUI: NestUI) extends TestInfo {
  private val stopwatch = new Stopwatch()

  import suiteRunner.{fileManager => fm, _}
  val fileManager = fm

  import fileManager._

  // Override to true to have the outcome of this test displayed
  // whether it passes or not; in general only failures are reported,
  // except for a . per passing test to show progress.
  def isEnumeratedTest = false

  private var _lastState: TestState = null
  private val _transcript = new TestTranscript

  def lastState                   = if (_lastState == null) Uninitialized(testFile) else _lastState
  def setLastState(s: TestState)  = _lastState = s
  def transcript: List[String]    = _transcript.fail ++ logFile.fileLines
  def pushTranscript(msg: String) = _transcript add msg

  val parentFile = testFile.getParentFile
  val kind       = parentFile.getName
  val fileBase   = basename(testFile.getName)
  val logFile    = new File(parentFile, s"$fileBase-$kind.log")
  val outFile    = logFile changeExtension "obj"
  val checkFile  = testFile changeExtension "check"
  val flagsFile  = testFile changeExtension "flags"
  val testIdent  = testFile.testIdent // e.g. pos/t1234

  lazy val outDir = { outFile.mkdirs() ; outFile }

  type RanOneTest = (Boolean, LogContext)

  def showCrashInfo(t: Throwable) {
    System.err.println(s"Crashed running test $testIdent: " + t)
    if (!nestUI.terse)
      System.err.println(stackTraceString(t))
  }
  protected def crashHandler: PartialFunction[Throwable, TestState] = {
    case t: InterruptedException =>
      genTimeout()
    case t: Throwable =>
      showCrashInfo(t)
      logFile.appendAll(stackTraceString(t))
      genCrash(t)
  }

  def genPass()                   = Pass(testFile)
  def genFail(reason: String)     = Fail(testFile, reason, _transcript.fail.toArray)
  def genTimeout()                = Fail(testFile, "timed out", _transcript.fail.toArray)
  def genCrash(caught: Throwable) = Crash(testFile, caught, _transcript.fail.toArray)
  def genUpdated()                = Updated(testFile)

  private def workerError(msg: String): Unit = System.err.println("Error: " + msg)

  def javac(files: List[File]): TestState = {
    // compile using command-line javac compiler
    val args = Seq(
      javacCmdPath,
      "-d",
      outDir.getAbsolutePath,
      "-classpath",
      joinPaths(outDir :: testClassPath),
      "-J-Duser.language=en",
      "-J-Duser.country=US"
    ) ++ (toolArgsFor(files)("javac")
    ) ++ (files.map(_.getAbsolutePath)
    )

    pushTranscript(args mkString " ")
    if (runCommand(args, logFile)) genPass() else {
      genFail("java compilation failed")
    }
  }

  def testPrompt = kind match {
    case "res"  => "nsc> "
    case _      => "% "
  }

  /** Evaluate an action body and update the test state.
   *  @param failFn optionally map a result to a test state.
   */
  def nextTestAction[T](body: => T)(failFn: PartialFunction[T, TestState]): T = {
    val result = body
    setLastState( if (failFn isDefinedAt result) failFn(result) else genPass() )
    result
  }
  def nextTestActionExpectTrue(reason: String, body: => Boolean): Boolean = (
    nextTestAction(body) { case false => genFail(reason) }
  )
  def nextTestActionFailing(reason: String): Boolean = nextTestActionExpectTrue(reason, false)

  private def assembleTestCommand(outDir: File, logFile: File): List[String] = {
    // check whether there is a ".javaopts" file
    val argsFile  = testFile changeExtension "javaopts"
    val javaopts = readOptionsFile(argsFile)
    if (javaopts.nonEmpty)
      nestUI.verbose(s"Found javaopts file '$argsFile', using options: '${javaopts.mkString(",")}'")

    // Note! As this currently functions, suiteRunner.javaOpts must precede argString
    // because when an option is repeated to java only the last one wins.
    // That means until now all the .javaopts files were being ignored because
    // they all attempt to change options which are also defined in
    // partest.java_opts, leading to debug output like:
    //
    // debug: Found javaopts file 'files/shootout/message.scala-2.javaopts', using options: '-Xss32k'
    // debug: java -Xss32k -Xss2m -Xms256M -Xmx1024M -classpath [...]
    val propertyOpts = propertyOptions(fork = true).map { case (k, v) => s"-D$k=$v" }

    val classpath = joinPaths(extraClasspath ++ testClassPath)

    javaCmdPath +: (
      (suiteRunner.javaOpts.split(' ') ++ extraJavaOptions ++ javaopts).filter(_ != "").toList ++ Seq(
        "-classpath",
        join(outDir.toString, classpath)
      ) ++ propertyOpts ++ Seq(
        "scala.tools.nsc.MainGenericRunner",
        "-usejavacp",
        "Test",
        "jvm"
      )
    )
  }

  def propertyOptions(fork: Boolean): List[(String, String)] = {
    val testFullPath = testFile.getAbsolutePath
    val extras =   if (nestUI.debug) List("partest.debug" -> "true") else Nil
    val immutablePropsToCheck = List[(String, String)](
      "file.encoding" -> "UTF-8",
      "user.language" -> "en",
      "user.country" -> "US"
    )
    val immutablePropsForkOnly = List[(String, String)](
      "java.library.path" -> logFile.getParentFile.getAbsolutePath,
    )
    val shared = List(
      "partest.output" -> ("" + outDir.getAbsolutePath),
      "partest.lib" -> ("" + libraryUnderTest.jfile.getAbsolutePath),
      "partest.reflect" -> ("" + reflectUnderTest.jfile.getAbsolutePath),
      "partest.comp" -> ("" + compilerUnderTest.jfile.getAbsolutePath),
      "partest.cwd" -> ("" + outDir.getParent),
      "partest.test-path" -> ("" + testFullPath),
      "partest.testname" -> ("" + fileBase),
      "javacmd" -> ("" + javaCmdPath),
      "javaccmd" -> ("" + javacCmdPath),
    ) ++ extras
    if (fork) {
      immutablePropsToCheck ++ immutablePropsForkOnly ++ shared
    } else {
      for ((k, requiredValue) <- immutablePropsToCheck) {
        val actual = System.getProperty(k)
        assert(actual == requiredValue, s"Unable to run test without forking as the current JVM has an incorrect system property. For $k, found $actual, required $requiredValue")
      }
      shared
    }
  }


  /** Runs command redirecting standard out and
   *  error out to output file.
   */
  protected def runCommand(args: Seq[String], outFile: File): Boolean = {
    //(Process(args) #> outFile !) == 0 or (Process(args) ! pl) == 0
    val pl = ProcessLogger(outFile)
    val nonzero = 17     // rounding down from 17.3
    def run: Int = {
      val p = Process(args) run pl
      try p.exitValue
      catch {
        case e: InterruptedException =>
          nestUI.verbose(s"Interrupted waiting for command to finish (${args mkString " "})")
          p.destroy
          nonzero
        case t: Throwable =>
          nestUI.verbose(s"Exception waiting for command to finish: $t (${args mkString " "})")
          p.destroy
          throw t
      }
      finally pl.close()
    }
    (pl buffer run) == 0
  }

  private def execTest(outDir: File, logFile: File): Boolean = {
    val cmd = assembleTestCommand(outDir, logFile)

    pushTranscript((cmd mkString s" \\$EOL  ") + " > " + logFile.getName)
    nextTestAction(runCommand(cmd, logFile)) {
      case false =>
        _transcript append EOL + logFile.fileContents
        genFail("non-zero exit code")
    }
  }

  def execTestInProcess(classesDir: File, log: File): Boolean = {
    stopwatch.pause()
    suiteRunner.synchronized {
      stopwatch.start()
      def run(): Unit = {
        StreamCapture.withExtraProperties(propertyOptions(fork = false).toMap) {
          try {
            val out = Files.newOutputStream(log.toPath, StandardOpenOption.APPEND)
            try {
              val loader = new URLClassLoader(classesDir.toURI.toURL :: Nil, getClass.getClassLoader)
              StreamCapture.capturingOutErr(out) {
                val cls = loader.loadClass("Test")
                val main = cls.getDeclaredMethod("main", classOf[Array[String]])
                try {
                  main.invoke(null, Array[String]("jvm"))
                } catch {
                  case ite: InvocationTargetException => throw ite.getCause
                }
              }
            }  finally {
              out.close()
            }
          } catch {
            case t: ControlThrowable => throw t
            case t: Throwable =>
              // We'll let the checkfile diffing report this failure
              Files.write(log.toPath, stackTraceString(t).getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND)
          }
        }
      }

      pushTranscript(s"<in process execution of $testIdent> > ${logFile.getName}")

      TrapExit(() => run()) match {
        case Left((status, throwable)) if status != 0 =>
          setLastState(genFail("non-zero exit code"))
          false
        case _ =>
          setLastState(genPass())
          true
      }
    }
  }

  override def toString = s"""Test($testIdent, lastState = $lastState)"""

  // result is unused
  def newTestWriters() = {
    val swr = new StringWriter
    val wr  = new PrintWriter(swr, true)
    // diff    = ""

    ((swr, wr))
  }

  def fail(what: Any) = {
    nestUI.verbose("scalac: compilation of "+what+" failed\n")
    false
  }

  /** Filter the check file for conditional blocks.
   *  The check file can contain lines of the form:
   *  `#partest java7`
   *  where the line contains a conventional flag name.
   *  If the flag tests true, succeeding lines are retained
   *  (removed on false) until the next #partest flag.
   *  A missing flag evaluates the same as true.
   */
  def filteredCheck: Seq[String] = {
    import scala.util.Properties.{javaVersion, isAvian}
    // use lines in block so labeled? Default to sorry, Charlie.
    def retainOn(expr: String) = {
      val f = expr.trim
      val allArgs = suiteRunner.scalacExtraArgs ++ suiteRunner.scalacOpts.split(' ')
      def flagWasSet(f: String) = allArgs contains f
      val (invert, token) =
        if (f startsWith "!") (true, f drop 1) else (false, f)
      val cond = token.trim match {
        case "java8"  => javaVersion startsWith "1.8"
        case "java7"  => javaVersion startsWith "1.7"
        case "java6"  => javaVersion startsWith "1.6"
        case "avian"  => isAvian
        case "true"   => true
        case "-optimise" | "-optimize"
                      => flagWasSet("-optimise") || flagWasSet("-optimize")
        case flag if flag startsWith "-"
                      => flagWasSet(flag)
        case rest     => rest.isEmpty
      }
      if (invert) !cond else cond
    }
    val prefix = "#partest"
    val b = new ListBuffer[String]()
    var on = true
    for (line <- file2String(checkFile).linesIfNonEmpty) {
      if (line startsWith prefix) {
        on = retainOn(line stripPrefix prefix)
      } else if (on) {
        b += line
      }
    }
    b.toList
  }

  // diff logfile checkfile
  def currentDiff = {
    val logged = file2String(logFile).linesIfNonEmpty.toList
    val (checked, checkname) = if (checkFile.canRead) (filteredCheck, checkFile.getName) else (Nil, "empty")
    compareContents(original = logged, revised = checked, originalName = logFile.getName, revisedName = checkname)
  }

  val gitRunner = List("/usr/local/bin/git", "/usr/bin/git") map (f => new java.io.File(f)) find (_.canRead)
  val gitDiffOptions = "--ignore-space-at-eol --no-index " + propOrEmpty("partest.git_diff_options")
    // --color=always --word-diff

  def gitDiff(f1: File, f2: File): Option[String] = {
    try gitRunner map { git =>
      val cmd  = s"$git diff $gitDiffOptions $f1 $f2"
      val diff = Process(cmd).lineStream_!.drop(4).map(_ + "\n").mkString

      "\n" + diff
    }
    catch { case t: Exception => None }
  }

  /** Normalize the log output by applying test-specific filters
   *  and fixing filesystem-specific paths.
   *
   *  Line filters are picked up from `filter: pattern` at the top of sources.
   *  The filtered line is detected with a simple "contains" test,
   *  and yes, "filter" means "filter out" in this context.
   *
   *  File paths are detected using the absolute path of the test root.
   *  A string that looks like a file path is normalized by replacing
   *  the leading segments (the root) with "\$ROOT" and by replacing
   *  any Windows backslashes with the one true file separator char.
   */
  def normalizeLog() {
    import scala.util.matching.Regex

    // Apply judiciously; there are line comments in the "stub implementations" error output.
    val slashes    = """[/\\]+""".r
    def squashSlashes(s: String) = slashes replaceAllIn (s, "/")

    // this string identifies a path and is also snipped from log output.
    val elided     = parentFile.getAbsolutePath

    // something to mark the elision in the log file (disabled)
    val ellipsis   = "" //".../"    // using * looks like a comment

    // no spaces in test file paths below root, because otherwise how to detect end of path string?
    val pathFinder = raw"""(?i)\Q${elided}${File.separator}\E([\${File.separator}\S]*)""".r
    def canonicalize(s: String): String = (
      pathFinder replaceAllIn (s, m =>
        Regex.quoteReplacement(ellipsis + squashSlashes(m group 1)))
    )

    def masters    = {
      val files = List(new File(parentFile, "filters"), new File(PathSettings.srcDir.path, "filters"))
      files filter (_.exists) flatMap (_.fileLines) map (_.trim) filter (s => !(s startsWith "#"))
    }
    val filters    = toolArgs("filter", split = false) ++ masters
    val elisions   = ListBuffer[String]()
    //def lineFilter(s: String): Boolean  = !(filters exists (s contains _))
    def lineFilter(s: String): Boolean  = (
      filters map (_.r) forall { r =>
        val res = (r findFirstIn s).isEmpty
        if (!res) elisions += s
        res
      }
    )

    logFile.mapInPlace(canonicalize)(lineFilter)
    if (nestUI.verbose && elisions.nonEmpty) {
      import nestUI.color._
      val emdash = bold(yellow("--"))
      pushTranscript(s"filtering ${logFile.getName}$EOL${elisions mkString (emdash, EOL + emdash, EOL)}")
    }
  }

  def diffIsOk: Boolean = {
    // always normalize the log first
    normalizeLog()
    val diff = currentDiff
    // if diff is not empty, is update needed?
    val updating: Option[Boolean] = (
      if (diff == "") None
      else Some(updateCheck)
    )
    pushTranscript(s"diff $logFile $checkFile")
    nextTestAction(updating) {
      case Some(true)  =>
        nestUI.verbose("Updating checkfile " + checkFile)
        checkFile writeAll file2String(logFile)
        genUpdated()
      case Some(false) =>
        // Get a word-highlighted diff from git if we can find it
        val bestDiff =
          if (updating.isEmpty) ""
          else if (checkFile.canRead)
            gitRunner match {
              case None => diff
              case _    => withTempFile(outFile, fileBase, filteredCheck) { f =>
                gitDiff(logFile, f) getOrElse diff
              }
            }
          else diff
        _transcript append bestDiff
        genFail("output differs")
        // TestState.fail("output differs", "output differs",
        // genFail("output differs")
        // TestState.Fail("output differs", bestDiff)
      case None        => genPass()  // redundant default case
    } getOrElse true
  }

  /** 1. Creates log file and output directory.
   *  2. Runs script function, providing log file and output directory as arguments.
   *     2b. or, just run the script without context and return a new context
   */
  def runInContext(body: => Boolean): (Boolean, LogContext) = {
    val (swr, wr) = newTestWriters()
    val succeeded = body
    (succeeded, LogContext(logFile, swr, wr))
  }

  /** Grouped files in group order, and lex order within each group. */
  def groupedFiles(sources: List[File]): List[List[File]] = (
    if (sources.tail.nonEmpty) {
      val grouped = sources groupBy (_.group)
      grouped.keys.toList.sorted map (k => grouped(k) sortBy (_.getName))
    }
    else List(sources)
  )

  /** Source files for the given test file. */
  def sources(file: File): List[File] = (
    if (file.isDirectory)
      file.listFiles.toList filter (_.isJavaOrScala)
    else
      List(file)
  )

  def newCompiler = new DirectCompiler(this)

  def attemptCompile(sources: List[File]): TestState = {
    val state = newCompiler.compile(flagsForCompilation(sources), sources)
    if (!state.isOk)
      _transcript append ("\n" + file2String(logFile))

    state
  }

  // snort or scarf all the contributing flags files
  def flagsForCompilation(sources: List[File]): List[String] = {
    val perTest  = readOptionsFile(flagsFile)
    val perGroup = if (testFile.isDirectory) {
      sources.flatMap(f => readOptionsFile(f changeExtension "flags"))
    } else Nil
    val perFile  = toolArgsFor(sources)("scalac")
    perTest ++ perGroup ++ perFile
  }

  // inspect sources for tool args
  def toolArgs(tool: String, split: Boolean = true): List[String] =
    toolArgsFor(sources(testFile))(tool, split)

  // inspect given files for tool args of the form `tool: args`
  // if args string ends in close comment, drop the `*` `/`
  // if split, parse the args string as command line.
  //
  def toolArgsFor(files: List[File])(tool: String, split: Boolean = true): List[String] = {
    def argsFor(f: File): List[String] = {
      import scala.tools.cmd.CommandLineParser.tokenize
      val max  = 10
      val tag  = s"$tool:"
      val endc = "*" + "/"    // be forgiving of /* scalac: ... */
      def stripped(s: String) = s.substring(s.indexOf(tag) + tag.length).stripSuffix(endc)
      def argsplitter(s: String) = if (split) tokenize(s) else List(s.trim())
      val src = Files.lines(f.toPath, codec.charSet)
      val args = try {
        val s = src.limit(max).filter(_.contains(tag)).map(stripped).findAny.orElse("")
        if (s == "") None else Some(s)
      } finally src.close()
      args.map(argsplitter).getOrElse(Nil)
    }
    files flatMap argsFor
  }

  abstract class CompileRound {
    def fs: List[File]
    def result: TestState
    def description: String

    def fsString = fs map (_.toString stripPrefix parentFile.toString + "/") mkString " "
    def isOk = result.isOk
    def mkScalacString(): String = s"""scalac $fsString"""
    override def toString = description + ( if (result.isOk) "" else "\n" + result.status )
  }
  case class OnlyJava(fs: List[File]) extends CompileRound {
    def description = s"""javac $fsString"""
    lazy val result = { pushTranscript(description) ; javac(fs) }
  }
  case class OnlyScala(fs: List[File]) extends CompileRound {
    def description = mkScalacString()
    lazy val result = { pushTranscript(description) ; attemptCompile(fs) }
  }
  case class ScalaAndJava(fs: List[File]) extends CompileRound {
    def description = mkScalacString()
    lazy val result = { pushTranscript(description) ; attemptCompile(fs) }
  }

  def compilationRounds(file: File): List[CompileRound] = (
    (groupedFiles(sources(file)) map mixedCompileGroup).flatten
  )
  def mixedCompileGroup(allFiles: List[File]): List[CompileRound] = {
    val (scalaFiles, javaFiles) = allFiles partition (_.isScala)
    val round1                  = if (scalaFiles.isEmpty) None else Some(ScalaAndJava(allFiles))
    val round2                  = if (javaFiles.isEmpty) None else Some(OnlyJava(javaFiles))

    List(round1, round2).flatten
  }

  def runPosTest(): TestState =
    if (checkFile.exists) genFail("unexpected check file for pos test (use -Xfatal-warnings with neg test to verify warnings)")
    else runTestCommon(true)

  def runNegTest() = runInContext {
    val rounds = compilationRounds(testFile)

    // failing means Does Not Compile
    val failing = rounds find (x => nextTestActionExpectTrue("compilation failed", x.isOk) == false)

    // which means passing if it checks and didn't crash the compiler
    // or, OK, we'll let you crash the compiler with a FatalError if you supply a check file
    def checked(r: CompileRound) = r.result match {
      case Crash(_, t, _) if !checkFile.canRead || !t.isInstanceOf[FatalError] => false
      case _ => diffIsOk
    }

    failing map (checked) getOrElse nextTestActionFailing("expected compilation failure")
  }

  def runTestCommon(andAlso: => Boolean): (Boolean, LogContext) = runInContext {
    compilationRounds(testFile).forall(x => nextTestActionExpectTrue("compilation failed", x.isOk)) && andAlso
  }

  def extraClasspath = kind match {
    case "specialized"  => List(PathSettings.srcSpecLib.fold(sys.error, identity))
    case _              => Nil
  }
  def extraJavaOptions = kind match {
    case "instrumented" => ("-javaagent:"+agentLib).split(' ')
    case _              => Array.empty[String]
  }

  def runResidentTest() = {
    // simulate resident compiler loop
    val prompt = "\nnsc> "
    val (swr, wr) = newTestWriters()

    nestUI.verbose(s"$this running test $fileBase")
    val dir = parentFile
    val resFile = new File(dir, fileBase + ".res")

    // run compiler in resident mode
    // $SCALAC -d "$os_dstbase".obj -Xresident -sourcepath . "$@"
    val sourcedir  = logFile.getParentFile.getAbsoluteFile
    val sourcepath = sourcedir.getAbsolutePath+File.separator
    nestUI.verbose("sourcepath: "+sourcepath)

    val argList = List(
      "-d", outDir.getAbsoluteFile.getPath,
      "-Xresident",
      "-sourcepath", sourcepath)

    // configure input/output files
    val logOut    = new FileOutputStream(logFile)
    val logWriter = new PrintStream(logOut, true)
    val resReader = new BufferedReader(new FileReader(resFile))
    val logConsoleWriter = new PrintWriter(new OutputStreamWriter(logOut), true)

    // create compiler
    val settings = new Settings(workerError)
    settings.sourcepath.value = sourcepath
    settings.classpath.value = joinPaths(fileManager.testClassPath)
    val reporter = new ConsoleReporter(settings, scala.Console.in, logConsoleWriter)
    val command = new CompilerCommand(argList, settings)
    object compiler extends Global(command.settings, reporter)

    def resCompile(line: String): Boolean = {
      // NestUI.verbose("compiling "+line)
      val cmdArgs = (line split ' ').toList map (fs => new File(dir, fs).getAbsolutePath)
      // NestUI.verbose("cmdArgs: "+cmdArgs)
      val sett = new Settings(workerError)
      sett.sourcepath.value = sourcepath
      val command = new CompilerCommand(cmdArgs, sett)
      // "scalac " + command.files.mkString(" ")
      pushTranscript("scalac " + command.files.mkString(" "))
      nextTestActionExpectTrue(
        "compilation failed",
        command.ok && {
          (new compiler.Run) compile command.files
          !reporter.hasErrors
        }
      )
    }
    def loop(): Boolean = {
      logWriter.print(prompt)
      resReader.readLine() match {
        case null | ""  => logWriter.close() ; true
        case line       => resCompile(line) && loop()
      }
    }
    // res/t687.res depends on ignoring its compilation failure
    // and just looking at the diff, so I made them all do that
    // because this is long enough.
    if (!Output.withRedirected(logWriter)(try loop() finally resReader.close()))
      setLastState(genPass())

    (diffIsOk, LogContext(logFile, swr, wr))
  }

  def run(): (TestState, Long) = {
    // javac runner, for one, would merely append to an existing log file, so just delete it before we start
    logFile.delete()
    stopwatch.start()

    if (kind == "neg" || (kind endsWith "-neg")) runNegTest()
    else kind match {
      case "pos"          => runPosTest()
      case "res"          => runResidentTest()
      case "scalap"       => runScalapTest()
      case "script"       => runScriptTest()
      case _              => runRunTest()
    }

    (lastState, stopwatch.stop)
  }

  private def runRunTest(): Unit = {
    val argsFile  = testFile changeExtension "javaopts"
    val javaopts = readOptionsFile(argsFile)
    val execInProcess = PartestDefaults.execInProcess && javaopts.isEmpty && !Set("specialized", "instrumented").contains(testFile.getParentFile.getName)
    def exec() = if (execInProcess) execTestInProcess(outDir, logFile) else execTest(outDir, logFile)
    runTestCommon(exec() && diffIsOk)
  }

  private def decompileClass(clazz: Class[_], isPackageObject: Boolean): String = {
    import scala.tools.scalap

    // TODO: remove use of reflection once Scala 2.11.0-RC1 is out
    // have to use reflection to work on both 2.11.0-M8 and 2.11.0-RC1.
    // Once we require only 2.11.0-RC1, replace the following block by:
    // import scalap.scalax.rules.scalasig.ByteCode
    // ByteCode forClass clazz bytes
    val bytes = {
      import scala.language.{reflectiveCalls, existentials}
      type ByteCode       = { def bytes: Array[Byte] }
      type ByteCodeModule = { def forClass(clazz: Class[_]): ByteCode }
      val ByteCode        = {
        val ByteCodeModuleCls =
          // RC1 package structure -- see: scala/scala#3588 and https://issues.scala-lang.org/browse/SI-8345
          (util.Try { Class.forName("scala.tools.scalap.scalax.rules.scalasig.ByteCode$") }
          // M8 package structure
           getOrElse  Class.forName("scala.tools.scalap.scalasig.ByteCode$"))
        ByteCodeModuleCls.getDeclaredFields()(0).get(null).asInstanceOf[ByteCodeModule]
      }
      ByteCode forClass clazz bytes
    }

    scalap.Main.decompileScala(bytes, isPackageObject)
  }

  def runScalapTest() = runTestCommon {
    val isPackageObject = testFile.getName startsWith "package"
    val className       = testFile.getName.stripSuffix(".scala").capitalize + (if (!isPackageObject) "" else ".package")
    val loader          = ScalaClassLoader.fromURLs(List(outDir.toURI.toURL), this.getClass.getClassLoader)
    logFile writeAll decompileClass(loader loadClass className, isPackageObject)
    diffIsOk
  }

  def runScriptTest() = {
    import scala.sys.process._
    val (swr, wr) = newTestWriters()

    val args = file2String(testFile changeExtension "args")
    val cmdFile = if (isWin) testFile changeExtension "bat" else testFile
    val succeeded = (((cmdFile + " " + args) #> logFile !) == 0) && diffIsOk

    (succeeded, LogContext(logFile, swr, wr))
  }

  def cleanup() {
    if (lastState.isOk)
      logFile.delete()
    if (!nestUI.debug)
      Directory(outDir).deleteRecursively()
  }

}

/** Loads `library.properties` from the jar. */
object Properties extends scala.util.PropertiesTrait {
  protected def propCategory    = "partest"
  protected def pickJarBasedOn  = classOf[SuiteRunner]
}

/** Used by SBT- and ConsoleRunner for running a set of tests. */
class SuiteRunner(
  val testSourcePath: String, // relative path, like "files", or "pending"
  val fileManager: FileManager,
  val updateCheck: Boolean,
  val failed: Boolean,
  val nestUI: NestUI,
  val javaCmdPath: String = PartestDefaults.javaCmd,
  val javacCmdPath: String = PartestDefaults.javacCmd,
  val scalacExtraArgs: Seq[String] = Seq.empty,
  val javaOpts: String = PartestDefaults.javaOpts,
  val scalacOpts: String = PartestDefaults.scalacOpts) {

  import PartestDefaults.{ numThreads, waitTime }

  setUncaughtHandler

  // TODO: make this immutable
  PathSettings.testSourcePath = testSourcePath

  val durations = collection.concurrent.TrieMap[File, Long]()

  def banner = {
    val baseDir = fileManager.compilerUnderTest.parent.toString
    def relativize(path: String) = path.replace(baseDir, s"$$baseDir").replace(PathSettings.srcDir.toString, "$sourceDir")
    val vmBin  = javaHome + fileSeparator + "bin"
    val vmName = "%s (build %s, %s)".format(javaVmName, javaVmVersion, javaVmInfo)

  s"""|Partest version:     ${Properties.versionNumberString}
      |Compiler under test: ${relativize(fileManager.compilerUnderTest.getAbsolutePath)}
      |Scala version is:    $versionMsg
      |Scalac options are:  ${(scalacExtraArgs ++ scalacOpts.split(' ')).mkString(" ")}
      |Compilation Path:    ${relativize(joinPaths(fileManager.testClassPath))}
      |Java binaries in:    $vmBin
      |Java runtime is:     $vmName
      |Java options are:    $javaOpts
      |baseDir:             $baseDir
      |sourceDir:           ${PathSettings.srcDir}
    """.stripMargin
    // |Available processors:       ${Runtime.getRuntime().availableProcessors()}
    // |Java Classpath:             ${sys.props("java.class.path")}
  }

  def onFinishTest(testFile: File, result: TestState, durationMs: Long): TestState = {
    durations(testFile) = durationMs
    result
  }

  def runTest(testFile: File): TestState = {
    val start = System.nanoTime()
    val runner = new Runner(testFile, this, nestUI)
    var stopwatchDuration: Option[Long] = None

    // when option "--failed" is provided execute test only if log
    // is present (which means it failed before)
    val state =
      if (failed && !runner.logFile.canRead)
        runner.genPass()
      else {
        val (state, durationMs) =
          try runner.run()
          catch {
            case t: Throwable => throw new RuntimeException(s"Error running $testFile", t)
          }
        stopwatchDuration = Some(durationMs)
        nestUI.reportTest(state, runner, durationMs)
        runner.cleanup()
        state
      }
    val end = System.nanoTime()
    val durationMs = stopwatchDuration.getOrElse(TimeUnit.NANOSECONDS.toMillis(end - start))
    onFinishTest(testFile, state, durationMs)
  }

  def runTestsForFiles(kindFiles: Array[File], kind: String): Array[TestState] = {
    nestUI.resetTestNumber(kindFiles.size)

    val pool              = Executors newFixedThreadPool numThreads
    val futures           = kindFiles map (f => pool submit callable(runTest(f.getAbsoluteFile)))

    pool.shutdown()
    Try (pool.awaitTermination(waitTime) {
      throw TimeoutException(waitTime)
    }) match {
      case Success(_) => futures map (_.get)
      case Failure(e) =>
        e match {
          case TimeoutException(d)      =>
            nestUI.warning("Thread pool timeout elapsed before all tests were complete!")
          case ie: InterruptedException =>
            nestUI.warning("Thread pool was interrupted")
            ie.printStackTrace()
        }
        pool.shutdownNow()     // little point in continuing
        // try to get as many completions as possible, in case someone cares
        val results = for (f <- futures) yield {
          try {
            Some(f.get(0, NANOSECONDS))
          } catch {
            case _: Throwable => None
          }
        }
        results.flatten
    }
  }

  class TestTranscript {
    private val buf = ListBuffer[String]()

    def add(action: String): this.type = { buf += action ; this }
    def append(text: String) { val s = buf.last ; buf.trimEnd(1) ; buf += (s + text) }

    // Colorize prompts according to pass/fail
    def fail: List[String] = {
      import nestUI.color._
      def pass(s: String) = bold(green("% ")) + s
      def fail(s: String) = bold(red("% ")) + s
      buf.toList match {
        case Nil  => Nil
        case xs   => (xs.init map pass) :+ fail(xs.last)
      }
    }
  }
}

case class TimeoutException(duration: Duration) extends RuntimeException

class LogContext(val file: File, val writers: Option[(StringWriter, PrintWriter)])

object LogContext {
  def apply(file: File, swr: StringWriter, wr: PrintWriter): LogContext = {
    require (file != null)
    new LogContext(file, Some((swr, wr)))
  }
  def apply(file: File): LogContext = new LogContext(file, None)
}

object Output {
  object outRedirect extends Redirecter(out)
  object errRedirect extends Redirecter(err)

  System.setOut(outRedirect)
  System.setErr(errRedirect)

  import scala.util.DynamicVariable
  private def out = java.lang.System.out
  private def err = java.lang.System.err
  private val redirVar = new DynamicVariable[Option[PrintStream]](None)

  class Redirecter(stream: PrintStream) extends PrintStream(new OutputStream {
    def write(b: Int) = withStream(_ write b)

    private def withStream(f: PrintStream => Unit) = f(redirVar.value getOrElse stream)

    override def write(b: Array[Byte]) = withStream(_ write b)
    override def write(b: Array[Byte], off: Int, len: Int) = withStream(_.write(b, off, len))
    override def flush = withStream(_.flush)
    override def close = withStream(_.close)
  })

  // this supports thread-safe nested output redirects
  def withRedirected[T](newstream: PrintStream)(func: => T): T = {
    // note down old redirect destination
    // this may be None in which case outRedirect and errRedirect print to stdout and stderr
    val saved = redirVar.value
    // set new redirecter
    // this one will redirect both out and err to newstream
    redirVar.value = Some(newstream)

    try func
    finally {
      newstream.flush()
      redirVar.value = saved
    }
  }
}
