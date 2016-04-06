import java.io.File
import sbt.Keys._
import sbt._

import spray.json._, DefaultJsonProtocol._

import gov.nasa.jpl.imce.sbt._
import gov.nasa.jpl.imce.sbt.ProjectHelper._

useGpg := true

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

lazy val specsRoot = SettingKey[File]("specs-root", "MagicDraw DynamicScripts Test Specification Directory")

lazy val runMDTests = taskKey[Unit]("Run MagicDraw DynamicScripts Unit Tests")

/*
 * For now, we can't compile in strict mode because the Scala macros used for generating the JSon adapters
 * results in a compilation warning:
 *
 * Warning:(1, 0) Unused import
 * / *
 * ^
 *
 */
lazy val core = Project("imce-magicdraw-dynamicscripts-batch", file("."))
  .enablePlugins(IMCEGitPlugin)
  .enablePlugins(IMCEReleasePlugin)
  .settings(dynamicScriptsResourceSettings(Some("imce.magicdraw.dynamicscripts.batch")))
  //.settings(IMCEPlugin.strictScalacFatalWarningsSettings)
  .settings(docSettings(diagrams=false))
  .settings(IMCEReleasePlugin.packageReleaseProcessSettings)
  .settings(
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

    resourceDirectory in Compile :=
      baseDirectory.value / "resources",

    // disable publishing the jar produced by `test:package`
    publishArtifact in(Test, packageBin) := false,

    // disable publishing the test API jar
    publishArtifact in(Test, packageDoc) := false,

    // disable publishing the test sources jar
    publishArtifact in(Test, packageSrc) := false,

    unmanagedClasspath in Compile <++= unmanagedJars in Compile,

    resolvers += "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases",

    // https://github.com/aparo/play-json-extra
    libraryDependencies += "io.megl" %% "play-json-extra" % "2.4.3",

    mdRoot := file("/opt/local/imce/users/nfr/tools/MagicDraw/cae_md18_0_sp5_mdk-2.3-RC3/"),

    specsRoot := baseDirectory.value / "tests",

    runMDTests <<= (mdRoot, specsRoot, javaHome, classDirectory in Compile, streams) map {
      (md_install_dir, tests_dir, jHome, class_dir, s) =>

        s.log.info(s"# md.install.dir=$md_install_dir")
        s.log.info(s"# test specs.dir=$tests_dir")

        val weaverJar: File = {
          val weaverJars = ((md_install_dir / "lib" / "aspectj") * "aspectjweaver-*.jar").get
          require(1 == weaverJars.size)
          weaverJars.head
        }

        val rtJar: File = {
          val rtJars = ((md_install_dir / "lib" / "aspectj") * "aspectjrt-*.jar").get
          require(1 == rtJars.size)
          rtJars.head
        }

        val scalaLib: File = {
          val scalaLibs = ((md_install_dir / "lib" / "scala") * "scala-library-*.jar").get
          require(1 == scalaLibs.size)
          scalaLibs.head
        }

        val xalanLib: File = {
          val lib = md_install_dir / "lib" / "xalan.jar"
          require(lib.exists)
          lib
        }

        val patchJar: File = {
          val lib = md_install_dir / "lib" / "patch.jar"
          require(lib.exists)
          lib
        }
        val libJars =
          (patchJar +: ((md_install_dir / "lib") ** "*.jar").get.filterNot(_ == patchJar).sorted)
            .mkString(File.pathSeparator)

        val classpath = class_dir + File.pathSeparator + libJars

        val cp = classpath.replaceAllLiterally(md_install_dir.toString, "<md.root>")

        s.log.info(s"# CLASSPATH: ${cp.split(File.pathSeparator).mkString("\n","\n","\n")}")
        s.log.info(s"# CLASSPATH: $cp")

        val options = ForkOptions(
          bootJars = Seq(weaverJar, rtJar, scalaLib, xalanLib),
          javaHome = jHome,
          outputStrategy = Some(StdoutOutput),
          workingDirectory = Some(md_install_dir),
          runJVMOptions = Seq(
            "-classpath", classpath
          ),
          envVars = Map(
            "FL_FORCE_USAGE" -> "true",
            "FL_SERVER_ADDRESS" -> "cae-lic01.jpl.nasa.gov",
            "FL_SERVER_PORT" -> "1101",
            "FL_EDITION" -> "enterprise",
            "DYNAMIC_SCRIPTS_TESTS_DIR" -> tests_dir.getAbsolutePath)
        )

        val arguments: Seq[String] = Seq(
          "gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.ExecuteDynamicScriptAsMagicDrawUnitTest"
        )

        val mainClass: String = "org.junit.runner.JUnitCore"

        val exitCode = Fork.java(options, mainClass +: arguments)
        s.log.info(s"exit: $exitCode")
    },

    parallelExecution in Test := false,

    fork in Test := true,

    testGrouping in Test <<=
      ( testGrouping in Test,
        mdRoot,
        specsRoot,
        javaHome,
        classDirectory in Compile,
        connectInput,
        outputStrategy,
        javaOptions,
        envVars,
        streams ) map {

        (original, md_install_dir, tests_dir, jHome, class_dir, cInput, outputS, jOpts, env, s) =>

          val weaverJar: File = {
            val weaverJars = ((md_install_dir / "lib" / "aspectj") * "aspectjweaver-*.jar").get
            require(1 == weaverJars.size)
            weaverJars.head
          }

          val rtJar: File = {
            val rtJars = ((md_install_dir / "lib" / "aspectj") * "aspectjrt-*.jar").get
            require(1 == rtJars.size)
            rtJars.head
          }

          val scalaLib: File = {
            val scalaLibs = ((md_install_dir / "lib" / "scala") * "scala-library-*.jar").get
            require(1 == scalaLibs.size)
            scalaLibs.head
          }

          val xalanLib: File = {
            val lib = md_install_dir / "lib" / "xalan.jar"
            require(lib.exists)
            lib
          }

          val patchJar: File = {
            val lib = md_install_dir / "lib" / "patch.jar"
            require(lib.exists)
            lib
          }
          val libJars =
            (patchJar +: ((md_install_dir / "lib") ** "*.jar").get.filterNot(_ == patchJar).sorted)
              .mkString(File.pathSeparator)

          val classpath = class_dir + File.pathSeparator + libJars

          original.map { group =>

            env.keySet.toList.sorted.foreach { k =>
              s.log.info(s"env[$k]=${env.get(k)}")
            }

            s.log.info(s"${jOpts.size} jOpts:\n"+jOpts.mkString("\n"))

            val forkOptions = ForkOptions(
              bootJars = Seq(weaverJar, rtJar, scalaLib, xalanLib),
              javaHome = jHome,
              connectInput = cInput,
              outputStrategy = outputS,
              runJVMOptions = jOpts ++ Seq(
                "-classpath", classpath
              ),
              workingDirectory = Some(md_install_dir),
              envVars = env +
                ("FL_FORCE_USAGE" -> "true") +
                ("FL_SERVER_ADDRESS" -> "cae-lic01.jpl.nasa.gov") +
                ("FL_SERVER_PORT" -> "1101") +
                ("FL_EDITION" -> "enterprise") +
                ("DYNAMIC_SCRIPTS_TESTS_DIR" -> tests_dir.getAbsolutePath)
            )

            s.log.info(s"# working directory: $md_install_dir")

            group.copy(runPolicy = Tests.SubProcess(forkOptions))
          }
      }
  )
  .settings(
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test,compile",

    libraryDependencies +=
      "gov.nasa.jpl.imce.magicdraw.plugins" %% "imce_md18_0_sp5_dynamic-scripts"
        % Versions_imce_md18_0_sp5_dynamic_scripts.version %
        "compile" withSources() withJavadoc() artifacts
        Artifact("imce_md18_0_sp5_dynamic-scripts", "zip", "zip", Some("resource"), Seq(), None, Map()),

    extractArchives := {},

//    extractArchives <<= (baseDirectory, update, streams) map {
//      (base, up, s) =>
//
//        val mdInstallDir = base / "target" / "md.package"
//        if (!mdInstallDir.exists) {
//
//          IO.createDirectory(mdInstallDir)
//
//          val pfilter: DependencyFilter = new DependencyFilter {
//            def apply(c: String, m: ModuleID, a: Artifact): Boolean =
//              (a.`type` == "zip" || a.`type` == "resource") &&
//                a.extension == "zip" &&
//                ( m.organization == "gov.nasa.jpl.cae.magicdraw.packages" ||
//                  m.organization == "gov.nasa.jpl.imce.magicdraw.plugins")
//          }
//
//          val ps: Seq[File] = up.matching(pfilter)
//          ps.foreach { zip =>
//            val files = IO.unzip(zip, mdInstallDir)
//            s.log.info(
//              s"=> created md.install.dir=$mdInstallDir with ${files.size} " +
//                s"files extracted from zip: ${zip.getName}")
//          }
//
//          val mdBinFolder = mdInstallDir / "bin"
//          require(mdBinFolder.exists, "md bin: $mdBinFolder")
//
//        } else {
//          s.log.info(
//            s"=> use existing md.install.dir=$mdInstallDir")
//        }
//
//    },

    //unmanagedJars in Compile <++= (baseDirectory, update, streams, extractArchives) map {
    unmanagedJars in Compile <++= (mdRoot, update, streams, extractArchives) map {
        (base, up, s, _) =>

        //val mdInstallDir = base / "target" / "md.package"
        val mdInstallDir = base

        val libJars = ((mdInstallDir / "lib") ** "*.jar").get
        s.log.info(s"jar libraries: ${libJars.size}")

        val pluginJars = ((mdInstallDir / "plugins" / "gov.nasa.jpl.magicdraw.dynamicScripts") ** "*.jar").get
        s.log.info(s"plugin libraries: ${pluginJars.size}")

          //val dsJars = ((mdInstallDir / "dynamicScripts") * "*" / "lib" ** "*.jar").get
        //s.log.info(s"jar dynamic script: ${dsJars.size}")

        //val mdJars = (libJars ++ dsJars).map { jar => Attributed.blank(jar) }
        val mdJars = (libJars ++ pluginJars).map { jar => Attributed.blank(jar) }

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

def dynamicScriptsResourceSettings(dynamicScriptsProjectName: Option[String] = None): Seq[Setting[_]] = {

  import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

  def addIfExists(f: File, name: String): Seq[(File, String)] =
    if (!f.exists) Seq()
    else Seq((f, name))

  val QUALIFIED_NAME = "^[a-zA-Z][\\w_]*(\\.[a-zA-Z][\\w_]*)*$".r

  Seq(
    // the '*-resource.zip' archive will start from: 'dynamicScripts/<dynamicScriptsProjectName>'
    com.typesafe.sbt.packager.Keys.topLevelDirectory in Universal := {
      val projectName = dynamicScriptsProjectName.getOrElse(baseDirectory.value.getName)
      require(
        QUALIFIED_NAME.pattern.matcher(projectName).matches,
        s"The project name, '$projectName` is not a valid Java qualified name")
      Some(projectName)
    },

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
      (base, bin, src, doc, binT, srcT, docT, s) =>
        val dir = base / "svn" / "org.omg.oti.magicdraw"
        val file2name = (dir ** "*.dynamicScripts").pair(relativeTo(dir)) ++
          (dir ** "*.mdzip").pair(relativeTo(dir)) ++
          com.typesafe.sbt.packager.MappingsHelper.directory(dir / "resources") ++
          com.typesafe.sbt.packager.MappingsHelper.directory(dir / "profiles") ++
          addIfExists(bin, "lib/" + bin.name) ++
          addIfExists(binT, "lib/" + binT.name) ++
          addIfExists(src, "lib.sources/" + src.name) ++
          addIfExists(srcT, "lib.sources/" + srcT.name) ++
          addIfExists(doc, "lib.javadoc/" + doc.name) ++
          addIfExists(docT, "lib.javadoc/" + docT.name)

        s.log.info(s"file2name entries: ${file2name.size}")
        s.log.info(file2name.mkString("\n"))

        file2name
    },

    artifacts <+= (name in Universal) { n => Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) },
    packagedArtifacts <+= (packageBin in Universal, name in Universal) map { (p, n) =>
      Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) -> p
    }
  )
}