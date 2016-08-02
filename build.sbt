import java.io.File
import sbt.Keys._
import sbt._

import spray.json._, DefaultJsonProtocol._

import gov.nasa.jpl.imce.sbt._
import gov.nasa.jpl.imce.sbt.ProjectHelper._

useGpg := true

updateOptions := updateOptions.value.withCachedResolution(true)

developers := List(
  Developer(
    id="rouquett",
    name="Nicolas F. Rouquette",
    email="nicolas.f.rouquette@jpl.nasa.gov",
    url=url("https://gateway.jpl.nasa.gov/personal/rouquett/default.aspx")))

import scala.io.Source
import scala.util.control.Exception._

def docSettings(diagrams:Boolean): Seq[Setting[_]] =
  Seq(
    sources in (Compile,doc) <<= (git.gitUncommittedChanges, sources in (Compile,compile)) map {
      (uncommitted, compileSources) =>
        if (uncommitted)
          Seq.empty
        else
          compileSources
    },

    sources in (Test,doc) <<= (git.gitUncommittedChanges, sources in (Test,compile)) map {
      (uncommitted, testSources) =>
        if (uncommitted)
          Seq.empty
        else
          testSources
    },

    scalacOptions in (Compile,doc) ++=
      (if (diagrams)
        Seq("-diagrams")
      else
        Seq()
        ) ++
        Seq(
          "-doc-title", name.value,
          "-doc-root-content", baseDirectory.value + "/rootdoc.txt"
        ),
    autoAPIMappings := ! git.gitUncommittedChanges.value,
    apiMappings <++=
      ( git.gitUncommittedChanges,
        dependencyClasspath in Compile in doc,
        IMCEKeys.nexusJavadocRepositoryRestAPIURL2RepositoryName,
        IMCEKeys.pomRepositoryPathRegex,
        streams ) map { (uncommitted, deps, repoURL2Name, repoPathRegex, s) =>
        if (uncommitted)
          Map[File, URL]()
        else
          (for {
            jar <- deps
            url <- jar.metadata.get(AttributeKey[ModuleID]("moduleId")).flatMap { moduleID =>
              val urls = for {
                (repoURL, repoName) <- repoURL2Name
                (query, match2publishF) = IMCEPlugin.nexusJavadocPOMResolveQueryURLAndPublishURL(
                  repoURL, repoName, moduleID)
                url <- nonFatalCatch[Option[URL]]
                  .withApply { (_: java.lang.Throwable) => None }
                  .apply({
                    val conn = query.openConnection.asInstanceOf[java.net.HttpURLConnection]
                    conn.setRequestMethod("GET")
                    conn.setDoOutput(true)
                    repoPathRegex
                      .findFirstMatchIn(Source.fromInputStream(conn.getInputStream).getLines.mkString)
                      .map { m =>
                        val javadocURL = match2publishF(m)
                        s.log.info(s"Javadoc for: $moduleID")
                        s.log.info(s"= mapped to: $javadocURL")
                        javadocURL
                      }
                  })
              } yield url
              urls.headOption
            }
          } yield jar.data -> url).toMap
      }
  )

resolvers := {
  val previous = resolvers.value
  if (git.gitUncommittedChanges.value)
    Seq[Resolver](Resolver.mavenLocal) ++ previous
  else
    previous
}

shellPrompt in ThisBuild := { state => Project.extract(state).currentRef.project + "> " }

lazy val mdRoot = SettingKey[File]("md-root", "MagicDraw Installation Directory")

lazy val testsInputsDir = SettingKey[File]("tests-inputs-dir", "Directory to scan for input *.json tests")

lazy val testsResultDir = SettingKey[File]("tests-result-dir", "Directory for the tests results to archive as the test resource artifact")

lazy val testsResultsSetupTask = taskKey[Unit]("Create the tests results directory")

lazy val mdJVMFlags = SettingKey[Seq[String]]("md-jvm-flags", "Extra JVM flags for running MD (e.g., debugging)")

lazy val core = Project("imce-magicdraw-dynamicscripts-batch", file("."))
  .enablePlugins(IMCEGitPlugin)
  .enablePlugins(IMCEReleasePlugin)
  .settings(dynamicScriptsResourceSettings("imce.magicdraw.dynamicscripts.batch"))
  .settings(IMCEPlugin.strictScalacFatalWarningsSettings)
  // enable when JPL has a Nexus Pro configured with a cache of published javadoc html pages.
  //.settings(docSettings(diagrams=false))
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

    IMCEKeys.licenseYearOrRange := "2014-2016",
    IMCEKeys.organizationInfo := IMCEPlugin.Organizations.oti,
    IMCEKeys.targetJDK := IMCEKeys.jdk18.value,

    organization := "gov.nasa.jpl.imce.magicdraw.dynamicscripts",
    organizationHomepage :=
      Some(url("https://github.jpl.nasa.gov/imce/gov.nasa.jpl.imce.team")),

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

    unmanagedClasspath in Compile <++= unmanagedJars in Compile,

    resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases",

    mdJVMFlags := Seq("-Xmx8G"), //
    // for debugging: Seq("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"),

    testsInputsDir := baseDirectory.value / "resources" / "tests",

    mdRoot := baseDirectory.value / "target" / "md.package",

    testsResultDir := baseDirectory.value / "target" / "md.testResults",

    scalaSource in Test := baseDirectory.value / "src" / "test" / "scala",

    testsResultsSetupTask := {

      val s = streams.value

      // Wipe any existing tests results directory and create a fresh one
      val resultsDir = testsResultDir.value
      if (resultsDir.exists) {
        s.log.info(s"# Deleting existing results directory: $resultsDir")
        IO.delete(resultsDir)
      }
      s.log.info(s"# Creating results directory: $resultsDir")
      IO.createDirectory(resultsDir)
      require(
        resultsDir.exists && resultsDir.canWrite,
        s"The created results directory should exist and be writeable: $resultsDir")

    },

    test in Test <<= (test in Test) dependsOn testsResultsSetupTask,

    parallelExecution in Test := false,

    fork in Test := true,

    testGrouping in Test := {
      val original = (testGrouping in Test).value
      val tests_dir = testsInputsDir.value
      val md_install_dir = mdRoot.value
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
      s.log.info(
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
      s.log.info(s"# MD BOOT CLASSPATH: ${mdBoot.mkString("\n", "\n", "\n")}")

      val mdClasspath =
        mdProperties
          .getProperty("CLASSPATH")
          .split(":")
          .map(md_install_dir / _)
          .toSeq
      s.log.info(s"# MD CLASSPATH: ${mdClasspath.mkString("\n", "\n", "\n")}")

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
      s.log.info(s"# IMCE BOOT: ${imceBoot.mkString("\n", "\n", "\n")}")

      val imcePrefix =
        imceSetupProperties
          .find(_.startsWith("IMCE_CLASSPATH_PREFIX"))
          .getOrElse("")
          .stripPrefix("IMCE_CLASSPATH_PREFIX=\"")
          .stripSuffix("\"")
          .split("\\\\+:")
          .map(md_install_dir / _)
          .toSeq
      s.log.info(s"# IMCE CLASSPATH Prefix: ${imcePrefix.mkString("\n", "\n", "\n")}")

      original.map { group =>

        s.log.info(s"# ${env.size} env properties")
        env.keySet.toList.sorted.foreach { k =>
          s.log.info(s"env[$k]=${env.get(k)}")
        }
        s.log.info(s"# ------")

        s.log.info(s"# ${jOpts.size} java options")
        s.log.info(jOpts.mkString("\n"))
        s.log.info(s"# ------")

        s.log.info(s"# ${jvmFlags.size} jvm flags")
        s.log.info(jvmFlags.mkString("\n"))
        s.log.info(s"# ------")

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

        s.log.info(s"# working directory: $md_install_dir")

        group.copy(runPolicy = Tests.SubProcess(forkOptions))
      }
    }
  )
  .dependsOnSourceProjectOrLibraryArtifacts(
    "imce-oti-mof-magicdraw-dynamicscripts",
    "imce.oti.mof.magicdraw.dynamicscripts",
    Seq(
      "gov.nasa.jpl.imce.oti" %% "imce-oti-mof-magicdraw-dynamicscripts"
        % Versions_imce_oti_mof_magicdraw_dynamicscripts.version % "compile" withSources() withJavadoc() artifacts
        Artifact("imce-oti-mof-magicdraw-dynamicscripts", "zip", "zip", Some("resource"), Seq(), None, Map())
    )
  )
  .settings(
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test,compile",

    extractArchives <<= (baseDirectory, update, streams) map {
      (base, up, s) =>

        val mdInstallDir = base / "target" / "md.package"
        if (!mdInstallDir.exists) {

          IO.createDirectory(mdInstallDir)

          val pfilter: DependencyFilter = new DependencyFilter {
            def apply(c: String, m: ModuleID, a: Artifact): Boolean =
              (a.`type` == "zip" || a.`type` == "resource") &&
                a.extension == "zip" &&
                ( m.organization == "gov.nasa.jpl.cae.magicdraw.packages" ||
                  m.organization == "gov.nasa.jpl.imce.magicdraw.plugins" )
          }

          val ps: Seq[File] = up.matching(pfilter)
          ps.foreach { zip =>
            val files = IO.unzip(zip, mdInstallDir)
            s.log.info(
              s"=> created md.install.dir=$mdInstallDir with ${files.size} " +
                s"files extracted from zip: ${zip.getName}")
          }

          val mdBinFolder = mdInstallDir / "bin"
          require(mdBinFolder.exists, "md bin: $mdBinFolder")

          val dsFolder = mdInstallDir / "dynamicScripts"
          IO.createDirectory(dsFolder)

          val zfilter: DependencyFilter = new DependencyFilter {
            def apply(c: String, m: ModuleID, a: Artifact): Boolean =
              c == "compile" &&
                (a.`type` == "zip" || a.`type` == "resource") &&
                a.extension == "zip" && {

                val filter =
                  m.organization == "org.omg.tiwg" ||
                    m.organization == "gov.nasa.jpl.imce.oti" ||
                    m.organization == "gov.nasa.jpl.imce.omf"

                if (!filter)
                  s.log.info(s"-- excluding: ${m.organization}, ${a.name}")

                filter
              }
          }
          val zs: Seq[File] = up.matching(zfilter)
          zs.foreach { zip =>
            val files = IO.unzip(zip, dsFolder)
            s.log.info(
              s"=> extracted dynamic scripts with ${files.size} from zip: ${zip.getName}")
          }

        } else {
          s.log.info(
            s"=> use existing md.install.dir=$mdInstallDir")
        }

    },

    unmanagedJars in Compile <++= (baseDirectory, update, streams, extractArchives) map {
        (base, up, s, _) =>

        val mdInstallDir = base / "target" / "md.package"

        val libJars = ((mdInstallDir / "lib") ** "*.jar").get
        s.log.info(s"jar libraries: ${libJars.size}")

        val pluginJars = ((mdInstallDir / "plugins" / "gov.nasa.jpl.magicdraw.dynamicScripts") ** "*.jar").get
        s.log.info(s"plugin libraries: ${pluginJars.size}")

        val dsJars = ((mdInstallDir / "dynamicScripts") * "*" / "lib" ** "*.jar").get
        s.log.info(s"jar dynamic script: ${dsJars.size}")

        val mdJars = (libJars ++ pluginJars ++ dsJars).map { jar => Attributed.blank(jar) }

        mdJars
    },

    compile <<= (compile in Compile) dependsOn extractArchives,

    IMCEKeys.nexusJavadocRepositoryRestAPIURL2RepositoryName := Map(
      "https://oss.sonatype.org/service/local" -> "releases",
      "https://cae-nexuspro.jpl.nasa.gov/nexus/service/local" -> "JPL",
      "https://cae-nexuspro.jpl.nasa.gov/nexus/content/groups/jpl.beta.group" -> "JPL Beta Group",
      "https://cae-nexuspro.jpl.nasa.gov/nexus/content/groups/jpl.public.group" -> "JPL Public Group"),
    IMCEKeys.pomRepositoryPathRegex := """\<repositoryPath\>\s*([^\"]*)\s*\<\/repositoryPath\>""".r

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
    mappings in Universal <++= (
      baseDirectory,
      packageBin in Compile,
      packageSrc in Compile,
      packageDoc in Compile,
      packageBin in Test,
      packageSrc in Test,
      packageDoc in Test,
      streams) map {
      (dir, bin, src, doc, binT, srcT, docT, s) =>
        (dir ** "*.md").pair(rebase(dir, projectName)) ++
        (dir / "resources" ***).pair(rebase(dir, projectName)) ++
        addIfExists(bin, projectName + "/lib/" + bin.name) ++
        addIfExists(binT, projectName + "/lib/" + binT.name) ++
        addIfExists(src, projectName + "/lib.sources/" + src.name) ++
        addIfExists(srcT, projectName + "/lib.sources/" + srcT.name) ++
        addIfExists(doc, projectName + "/lib.javadoc/" + doc.name) ++
        addIfExists(docT, projectName + "/lib.javadoc/" + docT.name)
    },

    artifacts <+= (name in Universal) { n => Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) },
    packagedArtifacts <+= (packageBin in Universal, name in Universal) map { (p, n) =>
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) -> p
    }
  )
}