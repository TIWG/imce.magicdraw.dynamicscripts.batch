import java.io.File
import sbt.Keys._
import sbt._
import scala.io.Source

import spray.json._, DefaultJsonProtocol._

import gov.nasa.jpl.imce.sbt._
import gov.nasa.jpl.imce.sbt.ProjectHelper._

updateOptions := updateOptions.value.withCachedResolution(true)

resolvers := {
  val previous = resolvers.value
  if (git.gitUncommittedChanges.value)
    Seq[Resolver](Resolver.mavenLocal) ++ previous
  else
    previous
}

shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }

// @see https://github.com/jrudolph/sbt-dependency-graph/issues/113
def zipFileSelector
( a: Artifact, f: File)
: Boolean
= a.`type` == "zip" || a.extension == "zip"

def pluginFileSelector
( a: Artifact, f: File)
: Boolean
= (a.`type` == "zip" || a.`type` == "resource") &&
  a.extension == "zip" &&
  a.name.endsWith("plugin_2.11")

def dsFileSelector
( a: Artifact, f: File)
: Boolean
= (a.`type` == "zip" || a.`type` == "resource") &&
  a.extension == "zip" &&
  ! a.name.startsWith("imce.third_party.") &&
  ! a.name.endsWith("plugin_2.11") &&
  ! a.classifier.getOrElse("").startsWith("part")

// @see https://github.com/jrudolph/sbt-dependency-graph/issues/113
def fromConfigurationReport
(report: ConfigurationReport,
 rootInfo: sbt.ModuleID,
 selector: (Artifact, File) => Boolean)
: net.virtualvoid.sbt.graph.ModuleGraph = {
  implicit def id(sbtId: sbt.ModuleID): net.virtualvoid.sbt.graph.ModuleId
  = net.virtualvoid.sbt.graph.ModuleId(sbtId.organization, sbtId.name, sbtId.revision)

  def moduleEdges(orgArt: OrganizationArtifactReport)
  : Seq[(net.virtualvoid.sbt.graph.Module, Seq[net.virtualvoid.sbt.graph.Edge])]
  = {
    val chosenVersion = orgArt.modules.find(!_.evicted).map(_.module.revision)
    orgArt.modules.map(moduleEdge(chosenVersion))
  }

  def moduleEdge(chosenVersion: Option[String])(report: ModuleReport)
  : (net.virtualvoid.sbt.graph.Module, Seq[net.virtualvoid.sbt.graph.Edge]) = {
    val evictedByVersion = if (report.evicted) chosenVersion else None

    val jarFile = report.artifacts.find(selector.tupled).map(_._2)
    (net.virtualvoid.sbt.graph.Module(
      id = report.module,
      license = report.licenses.headOption.map(_._1),
      evictedByVersion = evictedByVersion,
      jarFile = jarFile,
      error = report.problem),
      report.callers.map(caller ⇒ net.virtualvoid.sbt.graph.Edge(caller.caller, report.module)))
  }

  val (nodes, edges) = report.details.flatMap(moduleEdges).unzip
  val root = net.virtualvoid.sbt.graph.Module(rootInfo)

  net.virtualvoid.sbt.graph.ModuleGraph(root +: nodes, edges.flatten)
}

lazy val mdInstallDirectory = SettingKey[File]("md-install-directory", "MagicDraw Installation Directory")

mdInstallDirectory in Global :=
  baseDirectory.value / "target" / "md.package"

lazy val testsInputsDir = SettingKey[File]("tests-inputs-dir", "Directory to scan for input *.json tests")

lazy val testsResultDir = SettingKey[File]("tests-result-dir", "Directory for the tests results to archive as the test resource artifact")

lazy val testsResultsSetupTask = taskKey[Unit]("Create the tests results directory")

lazy val mdJVMFlags = SettingKey[Seq[String]]("md-jvm-flags", "Extra JVM flags for running MD (e.g., debugging)")

lazy val core = Project("imce-magicdraw-dynamicscripts-batch", file("."))
  .enablePlugins(IMCEGitPlugin)
  .enablePlugins(IMCEReleasePlugin)
  .settings(dynamicScriptsResourceSettings("imce.magicdraw.dynamicscripts.batch"))
  .settings(IMCEPlugin.strictScalacFatalWarningsSettings)
  .settings(IMCEReleasePlugin.packageReleaseProcessSettings )
  .settings(
    releaseProcess := Seq(
      IMCEReleasePlugin.clearSentinel,
      IMCEReleasePlugin.checkUncommittedChanges,
      sbtrelease.ReleaseStateTransformations.checkSnapshotDependencies,
      sbtrelease.ReleaseStateTransformations.inquireVersions,
      IMCEReleasePlugin.extractStep,
      IMCEReleasePlugin.setReleaseVersion,
      IMCEReleasePlugin.runCompile,
      sbtrelease.ReleaseStateTransformations.tagRelease,
      sbtrelease.ReleaseStateTransformations.publishArtifacts,
      sbtrelease.ReleaseStateTransformations.pushChanges,
      sbtrelease.ReleaseStateTransformations.runTest,
      IMCEReleasePlugin.successSentinel
    ),

    IMCEKeys.licenseYearOrRange := "2016",
    IMCEKeys.organizationInfo := IMCEPlugin.Organizations.oti,
    IMCEKeys.targetJDK := IMCEKeys.jdk18.value,

    buildInfoPackage := "imce.magicdraw.dynamicscripts.batch",
    buildInfoKeys ++= Seq[BuildInfoKey](BuildInfoKey.action("buildDateUTC") { buildUTCDate.value }),

    projectID := {
      val previous = projectID.value
      previous.extra(
        "build.date.utc" -> buildUTCDate.value,
        "artifact.kind" -> "magicdraw.library")
    },

    git.baseVersion := Versions.version,

    publishArtifact in Test := true,

    unmanagedClasspath in Compile ++= (unmanagedJars in Compile).value,

    resolvers += Resolver.bintrayRepo("jpl-imce", "gov.nasa.jpl.imce"),
    resolvers += Resolver.bintrayRepo("tiwg", "org.omg.tiwg"),

    mdJVMFlags := Seq("-Xmx8G"), //
    // for debugging: Seq("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"),

    testsInputsDir := baseDirectory.value / "resources" / "tests",

    testsResultDir := baseDirectory.value / "target" / "md.testResults",

    scalaSource in Test := baseDirectory.value / "src" / "test" / "scala",

    testsResultsSetupTask := {

      val s = streams.value

      // Wipe any existing tests results directory and create a fresh one
      val resultsDir = testsResultDir.value
      if (resultsDir.exists) {
        s.log.warn(s"# Deleting existing results directory: $resultsDir")
        IO.delete(resultsDir)
      }
      s.log.warn(s"# Creating results directory: $resultsDir")
      IO.createDirectory(resultsDir)
      require(
        resultsDir.exists && resultsDir.canWrite,
        s"The created results directory should exist and be writeable: $resultsDir")

    },

    test in Test := (test in Test).dependsOn(testsResultsSetupTask).value,

    parallelExecution in Test := false,

    fork in Test := true,

    testGrouping in Test := {
      val original = (testGrouping in Test).value
      val tests_dir = testsInputsDir.value
      val md_install_dir = mdInstallDirectory.value
      val tests_results_dir = testsResultDir.value
      val pas = (packageBin in Universal).value
      val jvmFlags = mdJVMFlags.value
      val jHome = javaHome.value
      val cInput = connectInput.value
      val jOpts = javaOptions.value
      val env = envVars.value
      val s = streams.value

      val testOutputFile = tests_results_dir.toPath.resolve("output.log").toFile

      val xlogger = new xsbti.Logger {

        def debug(msg: xsbti.F0[String]): Unit = append(msg())
        def error(msg: xsbti.F0[String]): Unit = append(msg())
        def info(msg: xsbti.F0[String]): Unit = append(msg())
        def warn(msg: xsbti.F0[String]): Unit = append(msg())
        def trace(exception: xsbti.F0[Throwable]): Unit = {
          val t = exception()
          append(t.getMessage)
          append(t.getStackTraceString)
        }

        def append(msg: String): Unit = {
          val pw = new java.io.PrintWriter(new java.io.FileWriter(testOutputFile, true))
          pw.println(msg)
          pw.flush()
          pw.close()
        }

      }

      val logger = new FullLogger(xlogger)

      val ds_dir = md_install_dir / "dynamicScripts"

      val files = IO.unzip(pas, ds_dir)
      s.log.warn(
        s"=> Installed ${files.size} " +
          s"files extracted from zip: $pas")

      val mdProperties = new java.util.Properties()
      IO.load(mdProperties, md_install_dir / "bin" / "magicdraw.properties")

      val mdBoot =
        mdProperties
          .getProperty("BOOT_CLASSPATH")
          .split(":")
          .map(md_install_dir / _)
          .toSeq
      s.log.warn(s"# MD BOOT CLASSPATH: ${mdBoot.mkString("\n", "\n", "\n")}")

      val mdClasspath =
        mdProperties
          .getProperty("CLASSPATH")
          .split(":")
          .map(md_install_dir / _)
          .toSeq
      s.log.warn(s"# MD CLASSPATH: ${mdClasspath.mkString("\n", "\n", "\n")}")

      val imceSetupProperties = IO.readLines(md_install_dir / "bin" / "magicdraw.imce.setup.sh")

      val imceBoot =
        imceSetupProperties
          .find(_.startsWith("IMCE_BOOT_CLASSPATH_PREFIX"))
          .getOrElse("")
          .stripPrefix("IMCE_BOOT_CLASSPATH_PREFIX=\"")
          .stripSuffix("\"")
          .split("\\\\+:")
          .map(md_install_dir / _)
          .toSeq
      s.log.warn(s"# IMCE BOOT: ${imceBoot.mkString("\n", "\n", "\n")}")

      val imcePrefix =
        imceSetupProperties
          .find(_.startsWith("IMCE_CLASSPATH_PREFIX"))
          .getOrElse("")
          .stripPrefix("IMCE_CLASSPATH_PREFIX=\"")
          .stripSuffix("\"")
          .split("\\\\+:")
          .map(md_install_dir / _)
          .toSeq
      s.log.warn(s"# IMCE CLASSPATH Prefix: ${imcePrefix.mkString("\n", "\n", "\n")}")

      original.map { group =>

        s.log.warn(s"# ${env.size} env properties")
        env.keySet.toList.sorted.foreach { k =>
          s.log.warn(s"env[$k]=${env.get(k)}")
        }
        s.log.warn(s"# ------")

        s.log.warn(s"# ${jOpts.size} java options")
        s.log.warn(jOpts.mkString("\n"))
        s.log.warn(s"# ------")

        s.log.warn(s"# ${jvmFlags.size} jvm flags")
        s.log.warn(jvmFlags.mkString("\n"))
        s.log.warn(s"# ------")

        val testPropertiesFile =
          md_install_dir.toPath.resolve("data/imce.properties").toFile

        val out = new java.io.PrintWriter(new java.io.FileWriter(testPropertiesFile))
        val in = Source.fromFile(md_install_dir.toPath.resolve("data/test.properties").toFile)
        for (line <- in.getLines) {
          if (line.startsWith("log4j.appender.R.File="))
            out.println(s"log4j.appender.R.File=$tests_results_dir/tests.log")
          else if (line.startsWith("log4j.appender.SO=")) {
            out.println(s"log4j.appender.SO=org.apache.log4j.RollingFileAppender")
            out.println(s"log4j.appender.SO.File=$tests_results_dir/console.log")
          }
          else
            out.println(line)
        }
        out.close()

        val forkOptions = ForkOptions(
          bootJars = imceBoot ++ mdBoot,
          javaHome = jHome,
          connectInput = cInput,
          outputStrategy = Some(LoggedOutput(logger)),
          runJVMOptions = jOpts ++ Seq(
            "-classpath", (imcePrefix ++ mdClasspath).mkString(File.pathSeparator),
            "-DLOCALCONFIG=false",
            "-DWINCONFIG=false",
            "-DHOME=" + md_install_dir.getAbsolutePath,
            s"-Ddebug.properties=$testPropertiesFile",
            "-Ddebug.properties.file=imce.properties"
          ) ++ jvmFlags,
          workingDirectory = Some(md_install_dir),
          envVars = env +
            ("debug.dir" -> md_install_dir.getAbsolutePath) +
            ("FL_FORCE_USAGE" -> "true") +
            ("FL_SERVER_ADDRESS" -> "cae-lic01.jpl.nasa.gov") +
            ("FL_SERVER_PORT" -> "1101") +
            ("FL_EDITION" -> "enterprise") +
            ("DYNAMIC_SCRIPTS_TESTS_DIR" -> tests_dir.getAbsolutePath) +
            ("DYNAMIC_SCRIPTS_RESULTS_DIR" -> tests_results_dir.getAbsolutePath)
        )

        s.log.warn(s"# working directory: $md_install_dir")

        group.copy(runPolicy = Tests.SubProcess(forkOptions))
      }
    }
  )
  .dependsOnSourceProjectOrLibraryArtifacts(
    "imce-oti-mof-magicdraw-dynamicscripts",
    "imce.oti.mof.magicdraw.dynamicscripts",
    Seq(
      "org.omg.tiwg" %% "imce.oti.mof.magicdraw.dynamicscripts"
        % Versions_imce_oti_mof_magicdraw_dynamicscripts.version
        % "compile" withSources() withJavadoc() artifacts
        Artifact("imce.oti.mof.magicdraw.dynamicscripts", "zip", "zip", Some("resource"), Seq(), None, Map())
    )
  )
  .settings(
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test,compile",

    extractArchives := {
      val s = streams.value
      val up = update.value
      val mdInstallDir = (mdInstallDirectory in ThisBuild).value
      if (!mdInstallDir.exists) {

        val crossV = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(projectID.value)
        val runtimeDepGraph =
          net.virtualvoid.sbt.graph.DependencyGraphKeys.ignoreMissingUpdate.value.configuration("runtime").get
        val compileDepGraph =
          net.virtualvoid.sbt.graph.DependencyGraphKeys.ignoreMissingUpdate.value.configuration("compile").get

        val mdParts = (for {
          cReport <- up.configurations
          if cReport.configuration == "compile"
          mReport <- cReport.modules
          if mReport.module.organization == "org.omg.tiwg.vendor.nomagic"
          (artifact, archive) <- mReport.artifacts
        } yield archive).sorted

        {
          s.log.warn(s"Extracting MagicDraw from ${mdParts.size} parts:")
          mdParts.foreach { p => s.log.warn(p.getAbsolutePath) }

          val merged = File.createTempFile("md_merged", ".zip")
          println(s"merged: ${merged.getAbsolutePath}")

          val zip = File.createTempFile("md_no_install", ".zip")
          println(s"zip: ${zip.getAbsolutePath}")

          val script = File.createTempFile("unzip_md", ".sh")
          println(s"script: ${script.getAbsolutePath}")

          val out = new java.io.PrintWriter(new java.io.FileOutputStream(script))
          out.println("#!/bin/bash")
          out.println(mdParts.map(_.getAbsolutePath).mkString("cat ", " ", s" > ${merged.getAbsolutePath}"))
          out.println(s"zip -FF ${merged.getAbsolutePath} --out ${zip.getAbsolutePath}")
          out.println(s"unzip -q ${zip.getAbsolutePath} -d ${mdInstallDir.getAbsolutePath}")
          out.close()

          val result = sbt.Process(command = "/bin/bash", arguments = Seq[String](script.getAbsolutePath)).!
          require(0 <= result && result <= 2, s"Failed to execute script (exit=$result): ${script.getAbsolutePath}")
          s.log.warn(s"Extracted.")
        }

        val pluginParts = (for {
          cReport <- up.configurations
          if cReport.configuration == "compile"
          mReport <- cReport.modules
          (artifact, archive) <- mReport.artifacts
          if artifact.name.startsWith("imce.dynamic_scripts.magicdraw.plugin")
          if artifact.classifier.getOrElse("").startsWith("part")
        } yield archive).sorted

        {
          s.log.warn(s"Extracting Plugin from ${pluginParts.size} parts:")
          pluginParts.foreach { p => s.log.warn(p.getAbsolutePath) }

          val merged = File.createTempFile("plugin_merged", ".zip")
          println(s"merged: ${merged.getAbsolutePath}")

          val zip = File.createTempFile("plugin_resource", ".zip")
          println(s"zip: ${zip.getAbsolutePath}")

          val script = File.createTempFile("unzip_plugin", ".sh")
          println(s"script: ${script.getAbsolutePath}")

          val out = new java.io.PrintWriter(new java.io.FileOutputStream(script))
          out.println("#!/bin/bash")
          out.println(pluginParts.map(_.getAbsolutePath).mkString("cat ", " ", s" > ${merged.getAbsolutePath}"))
          out.println(s"zip -FF ${merged.getAbsolutePath} --out ${zip.getAbsolutePath}")
          out.println(s"unzip -q ${zip.getAbsolutePath} -d ${mdInstallDir.getAbsolutePath}")
          out.close()

          val result = sbt.Process(command = "/bin/bash", arguments = Seq[String](script.getAbsolutePath)).!
          require(0 <= result && result <= 2, s"Failed to execute script (exit=$result): ${script.getAbsolutePath}")
          s.log.warn(s"Extracted.")

        }

        // @see https://github.com/jrudolph/sbt-dependency-graph/issues/113
        val g3 = fromConfigurationReport(compileDepGraph, crossV, dsFileSelector)

        for {
          module <- g3.nodes
          if module.id.organisation != "gov.nasa.jpl.cae.magicdraw.packages"
          archive <- module.jarFile
          extractFolder = mdInstallDir / "dynamicScripts"
          _ = IO.createDirectory(extractFolder)
          _ = s.log.warn(s"*** Extracting DynamicScripts: $archive")
          _ = s.log.warn(s"*** Extract to: $extractFolder")
          files = IO.unzip(archive, extractFolder)
          _ = require(files.nonEmpty)
          _ = s.log.warn(s"*** Extracted ${files.size} files")
        } yield ()

      } else
        s.log.warn(
          s"=> use existing md.install.dir=$mdInstallDir")
    },


    unmanagedJars in Compile := {
      val prev = (unmanagedJars in Compile).value
      val base = baseDirectory.value
      val s = streams.value
      val _ = extractArchives.value

      val mdInstallDir = base / "target" / "md.package"

      val allJars = (mdInstallDir ** "*.jar").get.map(Attributed.blank)
      s.log.warn(s"=> Adding ${allJars.size} unmanaged jars")

      allJars
    }
  )

def dynamicScriptsResourceSettings(projectName: String): Seq[Setting[_]] = {

  import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

  def addIfExists(f: File, name: String): Seq[(File, String)] =
    if (!f.exists) Seq()
    else Seq((f, name))

  val QUALIFIED_NAME = "^[a-zA-Z][\\w_]*(\\.[a-zA-Z][\\w_]*)*$".r

  Seq(
    // the '*-resource.zip' archive will start from: 'dynamicScripts/<dynamicScriptsProjectName>'
    com.typesafe.sbt.packager.Keys.topLevelDirectory in Universal := None,

    // name the '*-resource.zip' in the same way as other artifacts
    com.typesafe.sbt.packager.Keys.packageName in Universal :=
      normalizedName.value + "_" + scalaBinaryVersion.value + "-" + version.value + "-resource",

    // contents of the '*-resource.zip' to be produced by 'universal:packageBin'
    mappings in Universal in packageBin ++= {
      val dir = baseDirectory.value
      val bin = (packageBin in Compile).value
      val src = (packageSrc in Compile).value
      val doc = (packageDoc in Compile).value
      val binT = (packageBin in Test).value
      val srcT = (packageSrc in Test).value
      val docT = (packageDoc in Test).value

      (dir * ".classpath").pair(rebase(dir, projectName)) ++
      (dir * "*.md").pair(rebase(dir, projectName)) ++
      (dir / "resources" ***).pair(rebase(dir, projectName)) ++
      addIfExists(bin, projectName + "/lib/" + bin.name) ++
      addIfExists(binT, projectName + "/lib/" + binT.name) ++
      addIfExists(src, projectName + "/lib.sources/" + src.name) ++
      addIfExists(srcT, projectName + "/lib.sources/" + srcT.name) ++
      addIfExists(doc, projectName + "/lib.javadoc/" + doc.name) ++
      addIfExists(docT, projectName + "/lib.javadoc/" + docT.name)
    },

    artifacts += {
      val n = (name in Universal).value
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map())
    },
    packagedArtifacts += {
      val p = (packageBin in Universal).value
      val n = (name in Universal).value
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) -> p
    }
  )
}