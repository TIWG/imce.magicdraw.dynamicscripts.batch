# IMCE MagicDraw DynamicScripts Batch launcher

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
v
## Debugging

One-time debugging at the SBT prompt:

```
> set mdJVMFlags := Seq("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
> test
```

Alternatively, change the build.sbt file.