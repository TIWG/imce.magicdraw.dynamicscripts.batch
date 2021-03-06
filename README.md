# DynamicScripts batch launcher for MagicDraw

[![Build Status](https://travis-ci.org/TIWG/imce.magicdraw.dynamicscripts.batch.svg?branch=master)](https://travis-ci.org/TIWG/imce.magicdraw.dynamicscripts.batch)
 [ ![Download](https://api.bintray.com/packages/tiwg/org.omg.tiwg/imce.magicdraw.dynamicscripts.batch/images/download.svg) ](https://bintray.com/tiwg/org.omg.tiwg/imce.magicdraw.dynamicscripts.batch/_latestVersion)

This is a small utility to execute an IMCE MagicDraw DynamicScript as a batch MagicDraw Unit Test.

## Usage

(on the IMCE VM)

```
% source ~jenkins/sbt-aliases.sh
% sbtJPLBeta test
```

This will execute each IMCE DynamicScript specification (*.json) found in the [tests](tests) folder.

## IMCE DynamicScript Specification

An IMCE DynamicScript Specification is
a [MagicDrawTestSpec](src/test/scala/gov/nasa/jpl/imce/magicdraw/dynamicscripts/batch/json/MagicDrawTestSpec.scala),
a data structure specifying the following information to run a MagicDraw Unit Test:
 - A list of MagicDraw required plugin IDs
 - A list of IMCE MagicDraw dynamic script files

   Relative paths are assumed to be based on MagicDraw's installation folder.

 - A MagicDraw project location (local or teamwork)

 - The specification of an IMCE DynamicScript to be executed as a unit test


This project uses the [Play Framework JSon library](https://www.playframework.com/documentation/2.5.x/ScalaJson) to
generate, at compile time, JSon reader/writer converters for the data structures involved in
specifying the above information.

## Debugging

One-time debugging at the SBT prompt:

```
> set mdJVMFlags := Seq("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=<some port number>")
> test
```

Alternatively, change the build.sbt file.

## MD Unit Tests

It is surprisingly tricky to get MD Unit Tests to execute reliably and reproducibly.

- The MD installation folder is created from scratch in `target/md.package` when executing `test`

  When running SBT at the terminal, it may be useful to do `clean` to explicitly delete it.

- LOCALCONFIG, WINCONFIG to false (i.e. store all MD-related configuration info in `target/md.package`)

- Although the classpath is carefully constructed in the `ForkOptions` used to run tests, the actual classpath is different.

  The path in `ForkOptions` is carefully constructed to reflect what would be in `bin/magicdraw.imce.properties`.
  SBT adds the test framework libraries it uses. Fortunately, MD is able to handle this difference.
