Partest is a testing framework used to regression-test the Scala 2 compiler and standard library.

The source code for partest has returned to the scala/scala repository on both the 2.12.x and 2.13.x branches.  Hence, we are archiving this repo.

In Scala 3, partest has been replaced by [vulpix](https://dotty.epfl.ch/docs/contributing/testing.html).

## History

* Initially born in the Scala source repository
* Scala 2.11: Extracted out, 1.0.x version series
* Scala 2.12: Upgrade for Scala 2.12, bumped to 1.1.x
* Scala 2.13: [Folded back](https://github.com/scala/scala/pull/6566) into scala/scala, at https://github.com/scala/scala/tree/2.13.x/src/partest/scala/tools/partest
    * (for a short time there was a 1.2.x branch for 2.13, now abandoned)
* Scala 2.12, redux: [Folded back](https://github.com/scala/scala/pull/9169) into scala/scala
