
Partest is the testing framework used to regression-test the Scala compiler and standard library.

# Branches

* Scala 2.11: 1.0.x
* Scala 2.12: 1.1.x
* Scala 2.13: 1.2.x

# Where tests live

The compiler under test is instantiated by partest to compile the test files (or directories)
under the test sources directory (`test/files` by default). The test's output is compared against the
expected output specified by the corresponding `.check` file.
Failed tests will (typically) have a log file (`test/files/$kind/$testName-$kind.log`)
that differs from their check file (`test/files/$kind/$testName.check`).

Test categories (subdirectories under `test/files`)

  - `pos`: these files must compile successfully
  - `run`: in addition to compiling, `Test.main` is run and its output is compared against the test's `.check` file
  - `neg`: these files must not compile, with compiler output matching the expected output in the `.check` file
  - `jvm`: JVM-specific run tests

As partest links directly against the compiler being tested, it's cross-versioned against
the compiler version that it's intended for.

## Running partest

Partest is invoked from sbt.

The compiler to be tested must be on the classpath.
The corresponding jar or class directory is detected by [FileManager::findArtifact](https://github.com/scala/scala-partest/blob/master/src/main/scala/scala/tools/partest/nest/FileManager.scala#L123).

The classpath to run partest is specific to the compiler/libraries under test (script not included here, but see [the main test/partest script](https://github.com/scala/scala/blob/2.11.x/test/partest)).
It must provide the scala compiler to be tested and all its dependencies, and of course a compatible version of partest.
For details on the available arguments, see the  [ConsoleRunner argument spec](https://github.com/scala/scala-partest/blob/master/src/main/scala/scala/tools/partest/nest/ConsoleRunnerSpec.scala).
Here are some non-obvious useful options:

  - `--failed`: run only failed tests (ones that have a log file)
  - `--update-check`: overwrite check files with log files (where the latter exists)
  - `-Dpartest.scalac_opts=...` -> add compiler options
  - `-Dpartest.debug=true` -> print debug messages

## Advanced usage:

  - tests may consist of multiple files (the test name is the directory's name),
    and files (including java sources) in that directory are compiled in order by looking
    at `_$N` suffixes before the file's extension and compiling them grouped by $N, in ascending order.
  - jars in `test/files/lib` are expected to be on the classpath and may be used by tests
  - certain kinds of tests (instrumented/specialized) add additional jars to the classpath

## System properties available to tests:

  - `partest.output`: output directory (where classfiles go)
  - `partest.lib`: the path of the library (jar or class dir) being tested
  - `partest.reflect`: the path of scala-reflect (jar or class dir) being tested
  - `partest.comp`: the path of the compiler (jar or class dir) being tested
  - `partest.cwd`: partest working dir
  - `partest.test-path`
  - `partest.testname`
