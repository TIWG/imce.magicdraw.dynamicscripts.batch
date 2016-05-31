/*
 *
 * License Terms
 *
 * Copyright (c) 2014-2016, California Institute of Technology ("Caltech").
 * U.S. Government sponsorship acknowledged.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * *   Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * *   Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 *
 * *   Neither the name of Caltech nor its operating division, the Jet
 *    Propulsion Laboratory, nor the names of its contributors may be
 *    used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch

import java.awt.event.ActionEvent
import java.io.{File, FilenameFilter}
import java.lang.{Integer, System, Thread}
import java.net.URLClassLoader
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path, Paths}

import junit.framework.Test
import junit.framework.TestSuite
import junit.framework.TestListener

import com.nomagic.actions.NMAction
import com.nomagic.magicdraw.annotation.AnnotationManager
import com.nomagic.magicdraw.core.Application
import com.nomagic.magicdraw.core.Project
import com.nomagic.magicdraw.core.project.ProjectDescriptor
import com.nomagic.magicdraw.core.project.ProjectsManager
import com.nomagic.magicdraw.openapi.uml.SessionManager
import com.nomagic.magicdraw.plugins.Plugin
import com.nomagic.magicdraw.plugins.PluginDescriptor
import com.nomagic.magicdraw.plugins.PluginUtils
import com.nomagic.magicdraw.teamwork.application.TeamworkUtils
import com.nomagic.magicdraw.teamwork2.TeamworkService
import com.nomagic.magicdraw.teamwork2.ServerLoginInfo
import com.nomagic.task.RunnableWithProgress
import com.nomagic.task.ProgressStatus
import com.nomagic.magicdraw.ui.MagicDrawProgressStatusRunner
import com.nomagic.magicdraw.tests.MagicDrawTestCase
import com.nomagic.magicdraw.uml.symbols.{DiagramPresentationElement, PresentationElement}
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.{Diagram, Element, InstanceSpecification, LiteralString}

import gov.nasa.jpl.dynamicScripts.DynamicScriptsTypes.{DiagramContextMenuAction, DynamicActionScript, MainToolbarMenuAction}
import gov.nasa.jpl.dynamicScripts.magicdraw._
import gov.nasa.jpl.dynamicScripts.magicdraw.validation.MagicDrawValidationDataResults
import gov.nasa.jpl.dynamicScripts._
import gov.nasa.jpl.dynamicScripts.magicdraw.ClassLoaderHelper.ResolvedClassAndMethod
import gov.nasa.jpl.dynamicScripts.magicdraw._
import gov.nasa.jpl.dynamicScripts.magicdraw.actions._
import gov.nasa.jpl.dynamicScripts.magicdraw.utils.MDUML
import gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.json._
import gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.validation.OTIMagicDrawValidation

import org.junit._

import play.api.libs.json._

import scala.collection.JavaConversions._
import scala.collection.immutable._
import scala.language.implicitConversions
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.util.control.Exception._
import scala.{Array, Boolean, Enumeration, Int, Long, None, Option, Some, StringContext, Unit}
import scala.Predef.{classOf, require, ArrowAssoc, String, genericArrayOps}
import scalaz._
import Scalaz._

object MDTestAPI {

  def closeProjectNoSave(pManager: ProjectsManager): Unit =
    pManager.closeProjectNoSave()

}

@scala.deprecated("", "")
class MDTestAPI() {}

object ExecuteDynamicScriptAsMagicDrawUnitTest {

  def parseTestSpecification
  (specPath: Path)
  : Try[JsResult[MagicDrawTestSpec]]
  = nonFatalCatch[Try[MagicDrawTestSpec]]
    .withApply { (t: java.lang.Throwable) => Failure( t ) }
    .apply {
      val spec = scala.io.Source.fromFile(specPath.toFile).mkString
      val json = Json.parse(spec)
      val info = Json.fromJson[MagicDrawTestSpec](json)
      Success( info )
    }

  def suite: Test = {

    val t0 = SimpleMagicDrawTestSpec(
      requiredPlugins=List("a"),
      dynamicScriptFiles=List("f.dynamicScripts"),
      projectLocation = None,
      testScript = InvokeToolbarMenu(className="a.b", methodName="c"))

    val t0spec = MagicDrawTestSpec.formats.writes(t0)
    System.out.println(t0spec)

    val t1 = SimpleMagicDrawTestSpec(
      requiredPlugins=List("a"),
      dynamicScriptFiles=List("f.dynamicScripts"),
      projectLocation = Some(MagicDrawLocalProjectLocation(localProjectFile="tests/t1.mdzip")),
      testScript = InvokeToolbarMenu(className="a.b", methodName="c"))

    val t1spec = MagicDrawTestSpec.formats.writes(t1)
    System.out.println(t1spec)

    val jsonFilter = new java.util.function.BiPredicate[Path, BasicFileAttributes] {

      override def test( t: Path, u: BasicFileAttributes ): Boolean =
        t.toFile.getName.endsWith(".json") && u.isRegularFile

    }

    val s = new TestSuite()

    Option.apply( System.getenv("DYNAMIC_SCRIPTS_TESTS_DIR") ) match {
      case None =>
        Assert.fail("# No 'DYNAMIC_SCRIPTS_TESTS_DIR' environment variable")

      case Some(testsDir) =>
        System.out.println(s"# Use DYNAMIC_SCRIPTS_TESTS_DIR=$testsDir")
        val testsFolder = new File(testsDir).toPath
        val testsSpecs = Files.find(testsFolder, 1, jsonFilter)
        for {
          specPath <- testsSpecs.iterator()
          specInfo <- {
            val parsed: Try[JsResult[MagicDrawTestSpec]] = parseTestSpecification(specPath)
            parsed match {
              case Failure(t) =>
                System.out.println(t.getMessage)
                None

              case Success(JsError(errors)) =>
                val message =
                  s"# Error: Failed to parse test specification:\nfile: $specPath\n"+
                  errors.mkString("\n","\n","\n")
                Assert.fail(message)
                None

              case Success(JsSuccess(info, _)) =>
                Some(info)
            }
          }
        } {
          s.addTest( new ExecuteDynamicScriptAsMagicDrawUnitTest(specInfo) )
        }
    }

    s
  }

}

object AuditReportMode extends Enumeration {
  type AuditReportMode = Value
  val SERIALIZATION, SIGNATURE = Value
}

class ExecuteDynamicScriptAsMagicDrawUnitTest
( spec: MagicDrawTestSpec )
  extends MagicDrawTestCase( "test_MagicDrawTestSpec", spec.testName ) {

  import junit.framework.Assert._
  import AuditReportMode._
  import MDTestAPI._

  val a = Application.getInstance
  val pManager: ProjectsManager = a.getProjectsManager

  var stepCounter: Int = 0
  def step: Int = {
    stepCounter = stepCounter + 1
    stepCounter
  }

  var testProject: Option[Project] = None

  val mode: AuditReportMode = SERIALIZATION

  override def getRequiredPlugins: java.util.List[String] =
    spec.requiredPlugins

  val dynamicScriptFiles
  : List[String]
  = nonFatalCatch[List[String]]
    .withApply { (t: java.lang.Throwable) =>
      fail( "Error resolving paths from DYNAMIC_SCRIPTS_FILESt\n"+t.getMessage )
      List[String]()
    }
    .apply {
      val installRoot = Paths.get(MDUML.getInstallRoot)
      System.out.println(s"# md.install.root=$installRoot")
      for {
        file <- spec.dynamicScriptFiles
        path = Paths.get(file)
        dsFile = if (path.isAbsolute) path else installRoot.resolve(path)
        _ = System.out.println(s"# entry: $file\n# file: $dsFile\n# exists? ${dsFile.toFile.exists}")
      } yield dsFile.toFile.getAbsolutePath
    }

  override def setUpTest(): Unit = {
    super.setUpTest()

    val id2startedPlugin
    : Map[String, Plugin]
    = PluginUtils.getPlugins map { p => p.getDescriptor.getID -> p } toMap

    val missingPlugins: List[String] = for {
      requiredPlugin <- getRequiredPlugins.toList
      if !id2startedPlugin.contains( requiredPlugin )
    } yield requiredPlugin

    assertTrue(
      s"${missingPlugins.size} have not been started: "+
      s"${missingPlugins.mkString( "\n", "\n", "\n Check the test configuration" )}",
      missingPlugins.isEmpty )

    val t0 = System.currentTimeMillis

    val dsPlugin = DynamicScriptsPlugin.getInstance
    dsPlugin.updateRegistryForConfigurationFiles( dynamicScriptFiles ) match {
      case None =>
        val t1 = System.currentTimeMillis
        System.out.println( step+") successfully loaded dynamic scripts files" +
          " in " + prettyDurationFromTo(t0, t1))
      case Some( errorMessage ) =>
        fail( errorMessage )
    }

    spec.projectLocation match {

      case None =>
        ()

      case Some(loc: MagicDrawLocalProjectLocation) =>
        nonFatalCatch[Try[Project]]
          .withApply { (t: java.lang.Throwable) =>
            fail(t.getMessage)
          }
          .apply {
            val installRoot = Paths.get(MDUML.getInstallRoot)
            val localProjectPath = Paths.get(loc.localProjectFile)
            val localProjectAbsoluteFile =
              if (localProjectPath.isAbsolute)
                localProjectPath.toString
              else {
                val resolvedProjectPath = installRoot.resolve(localProjectPath)
                require(resolvedProjectPath.isAbsolute)
                resolvedProjectPath.toString
              }

            val t0 = System.currentTimeMillis
            System.out.println(step+") opening local project: " + localProjectAbsoluteFile)
            Option.apply(super.openProject(localProjectAbsoluteFile)) match {
              case None =>
                fail("Failed to open local project: " + localProjectAbsoluteFile)

              case Some(p) =>
                val t1 = System.currentTimeMillis
                System.out.println(
                  step+") successfully opened local project: " + localProjectAbsoluteFile +
                  " in " + prettyDurationFromTo(t0, t1))
                testProject = Some(p)
            }
          }

      case Some(loc: MagicDrawTeamworkProjectLocation) =>

        import loc._

        val t0 = System.currentTimeMillis

        val secure = true

        /*
         * Questions for NoMagic:
         *
         * 1) TeamworkUtils.authenticate seems redundant with TeamworkUtils.loginWithSession
         * However, authenticate() includes the secure boolean flag whereas loginWithSession() doesn't.
         */
        if (! TeamworkUtils.authenticate( teamworkServer, teamworkPort, secure, teamworkUser, teamworkPassword ))
          fail("Cannot authenticate "+server_connection_info )

        else {
          System.out.println(
            step+") successfully authenticated for " + server_connection_info)


          /*
           * Questions for NoMagic:
           *
           * 2) TeamworkUtils.loginWithSession returns a com.nomagic.teamwork.common.users.SessionInfo
           * There is no MD OpenAPI info about SessionInfo.
           * What should we do with it, if anything?
           */
          Option
            .apply(TeamworkUtils.loginWithSession(teamworkServer, teamworkPort, teamworkUser, teamworkPassword)) match {
            case None =>
              fail("Cannot login on Teamwork server: " + server_connection_info)

            case Some(s) =>
              System.out.println(
                step+") successful server login for: " + server_connection_info)

              nonFatalCatch[Try[Project]]
                .withApply { (t: java.lang.Throwable) =>
                  fail(t.getMessage)
                }
                .apply {
                  val pDescriptor = TeamworkUtils.getRemoteProjectDescriptorByQualifiedName(teamworkProjectPath)
                  System.out.println(
                    step+") successfully retrieved the descriptor for: " + server_connection_info)

                  val a = Application.getInstance
                  val pManager: ProjectsManager = a.getProjectsManager
                  val silent = true
                  pManager.loadProject(pDescriptor, silent)

                  Option.apply(a.getProject) match {
                    case None =>
                      fail("Failed to load project: " + server_connection_info)

                    case Some(p) =>
                      val t1 = System.currentTimeMillis
                      System.out.println(
                        step+") successfully opened teamwork project: " + server_connection_info + " in " +
                          prettyDurationFromTo(t0, t1))
                      testProject = Some(p)
                  }
                }
          }
        }
    }
  }

  override def tearDownTest(): Unit = {
    val t0 = System.currentTimeMillis

    /*
     * Question for NoMagic:
     *
     * 3) permission to use this method for unit testing.
     * As the name suggest, we'd prefer to make sure MD closes the teamwork project without
     * attempting any kind of update (if something has been modified) or without any consideration
     * for any unsaved changed (if any such change has been made)
     */
    closeProjectNoSave(pManager)

    spec.projectLocation match {

      case None =>
        ()

      case Some(loc: MagicDrawLocalProjectLocation) =>
        ()

      case Some(loc: MagicDrawTeamworkProjectLocation) =>
        if (!TeamworkUtils.logout)
          fail("Failed to logout from teamwork server: " + loc.server_connection_info)
        else {
          val t1 = System.currentTimeMillis
          System.out.println(
            step+") successfully logout from: " + loc.server_connection_info +
            " in " + prettyDurationFromTo(t0, t1))
        }
    }

  }

  def test_MagicDrawTestSpec(): Unit = {
    val t0 = System.currentTimeMillis

    val annotationManager = AnnotationManager.getInstance
    assertNotNull( annotationManager )

    val sm = SessionManager.getInstance

    for {
      p <- testProject
    } yield {
      if (sm.isSessionCreated(p))
        sm.closeSession(p)

      ()
    }

    val ev: ActionEvent = null

    val result = invokeDynamicScriptAction(testProject, ev)

    val t1 = System.currentTimeMillis

    result match {
      case Failure(t) =>
        System.out.println( step+") error executing DynamicScript in "+prettyDurationFromTo(t0, t1) )
        t.printStackTrace(System.out)

      case Success(None) =>
        System.out.println( step+") successfully ran DynamicScript in "+prettyDurationFromTo(t0, t1) )

      case Success(Some(mdValidationDataResults)) =>
        System.out.println( step+") ran DynamicScript in "+prettyDurationFromTo(t0, t1) )
        System.out.println(mdValidationDataResults)

    }

  }

  def invokeDynamicScriptAction
  (p: Option[Project],
   ev: ActionEvent)
  : Try[Option[MagicDrawValidationDataResults]]
  = spec.testScript match {
      case i: InvokeToolbarMenu =>
        invokeToolbarMenuAction(ev, i)
      case i: InvokeDiagramContextMenuActionForSelection =>
        p match {
          case None =>
            Failure(new java.lang.IllegalArgumentException(s"A DiagramContextMenuAction test requires a project"))
          case Some(project) =>
            invokeDiagramContextMenuActionForSelection(project, ev, i)
        }
    }

  def invokeToolbarMenuAction
  (ev: ActionEvent,
   i: InvokeToolbarMenu)
  : Try[Option[MagicDrawValidationDataResults]]
  = {
    val dsPlugin = DynamicScriptsPlugin.getInstance

    val reg: DynamicScriptsRegistry = dsPlugin.getDynamicScriptsRegistry

    val scripts = for {
      ( _, menus ) <- reg.toolbarMenuPathActions
      menu <- menus
      script <- menu.scripts
      _ = System.out.println(s"script class: ${script.className.jname}, method: ${script.methodName.sname}")
      if script.className.jname == spec.dynamicScriptClass && script.methodName.sname == spec.dynamicScriptMethod
    } yield script

    assertTrue(
      "There should be exactly 1 DynamicScript.MainToolbarAction "+
        "(class="+spec.dynamicScriptClass+",method="+spec.dynamicScriptMethod+") but found "+scripts.size+" matches "+
        "(there are "+reg.toolbarMenuPathActions.size+" scripts)",
      scripts.size == 1 )

    val script = scripts.head
    val ds = DynamicScriptsLaunchToolbarMenuAction( script, script.name.hname )

    // @todo
    // Normally, it should be sufficient to invoke the MD DynamicScript action like this:
    //
    // ds.actionPerformed(ev)
    //
    // However, this reports errors using the MD Gui which is not available when there is no project opened.
    // The workaround is to report the error as a test failure.

    val previousTime = System.currentTimeMillis()
    val message = ds.action.prettyPrint( "" ) + "\n"

    ClassLoaderHelper.createDynamicScriptClassLoader( ds.action ) match {
      case Failure(t) =>
        throw new java.lang.AssertionError(
          s"Failed to load dynamic script class $message\n${t.getMessage}", t)

      case Success(scriptCL: URLClassLoader) => {
        val localClassLoader = Thread.currentThread().getContextClassLoader
        Thread.currentThread().setContextClassLoader(scriptCL)

        try {
          ClassLoaderHelper.lookupClassAndMethod(scriptCL, ds.action,
            classOf[Project], classOf[ActionEvent], classOf[MainToolbarMenuAction]) match {
            case Failure(t) =>
              throw new java.lang.AssertionError(
                s"Failed to lookup dynamic script class & method $message\n${t.getMessage}", t)

            case Success(cm: ResolvedClassAndMethod) =>
              ClassLoaderHelper.ignoreResultOrthrowFailure(
                ClassLoaderHelper
                  .invokeAndReport(previousTime, Application.getInstance().getProject, ev, cm)
              )
          }
        }
        finally {
          Thread.currentThread().setContextClassLoader(localClassLoader)
        }
      }
    }

    System.out.println(s"Successfully invoked: ${ds.action}")
    Success(None)
  }

  def invokeDiagramContextMenuActionForSelection
  (p: Project,
   ev: ActionEvent,
   i: InvokeDiagramContextMenuActionForSelection)
  : Try[Option[MagicDrawValidationDataResults]]
  = {
    val dsPlugin = DynamicScriptsPlugin.getInstance

    val reg: DynamicScriptsRegistry = dsPlugin.getDynamicScriptsRegistry

    val scripts: Iterable[DynamicScriptsTypes.DynamicActionScript] = for {
      (_, instanceScripts) <- reg.classifierActions
      actionScripts <- instanceScripts
      script <- actionScripts.scripts
      _ = System.out.println(s"script class: ${script.className.jname}, method: ${script.methodName.sname}")
      if script.className.jname == spec.dynamicScriptClass && script.methodName.sname == spec.dynamicScriptMethod
    } yield script

    assertTrue(
      "There should be exactly 1 DynamicScript.MainToolbarAction " +
        "(class=" + spec.dynamicScriptClass + ",method=" + spec.dynamicScriptMethod + ") but found " + scripts.size + " matches " +
        "(there are " + reg.toolbarMenuPathActions.size + " scripts)",
      scripts.size == 1)

    ( Option.apply(p.getElementByID(i.diagramID)),
      Option.apply(p.getElementByID(i.instanceSpecificationID)) ) match {

      case (Some(diagram: Diagram), Some(dsInstance: InstanceSpecification)) =>
        Option.apply(p.getDiagram(diagram)) match {
          case Some(dpe: DiagramPresentationElement) =>
            invokeDiagramContextMenuActionForSelection(p, ev, i, dpe, dsInstance)
          case _ =>
            Failure(new java.lang.IllegalArgumentException(s"Cannot find diagram in=$i"))
        }
      case _ =>
        Failure(new java.lang.IllegalArgumentException(s"Cannot find instance specification in=$i"))

    }
  }

  def invokeDiagramContextMenuActionForSelection
  (p: Project,
   ev: ActionEvent,
   i: InvokeDiagramContextMenuActionForSelection,
   dpe: DiagramPresentationElement,
   dsInstance: InstanceSpecification)
  : Try[Option[MagicDrawValidationDataResults]]
  = {
    import helpers._

    for {
      comment <- dsInstance.getOwnedComment
      commentBody = comment.getBody
      if "selection" == commentBody
      annotated = comment.getAnnotatedElement.to[List]
      if annotated.nonEmpty
      classNames <- dsInstance.getValuesOfFeatureSlot("className")
      methodNames <- dsInstance.getValuesOfFeatureSlot("methodName")
    } (classNames, methodNames) match {
      case ((c: LiteralString) :: Nil, (m: LiteralString) :: Nil) =>
        (c.value, m.value) match {
          case (Some(className), Some(methodName)) =>
            val invokeTriggerElement = annotated.head
            val invokeTriggerView = dpe.findPresentationElement(invokeTriggerElement, null)
            val peSelection = for {
              e <- annotated
              pe = dpe.findPresentationElement(e, null)
              _ = if (null == pe) {
                System.out.println(s"Invoking $className / $methodName: there is no presentation element for $e")
              }
              if null != pe
            } yield pe

            return invoke(
              p, ev, dsInstance,
              className, methodName,
              dpe, invokeTriggerView, invokeTriggerElement, peSelection)
          case (_, _) =>
            ()
        }
      case (_, _) =>
        ()
    }

    val otiV = OTIMagicDrawValidation(p)

    for {
      vInfo <-
      otiV.constructValidationInfo(
        otiV.MD_OTI_ValidationConstraint_UnresolvedCrossReference,
        Some("Check the instance specification details"),
        Nil)
      validation =
      otiV.makeMDIllegalArgumentExceptionValidation(
        s"*** Ill-formed DiagramContextMenuActionForSelection instance specification ***",
        Map(dsInstance -> List(vInfo)))
      result <-
      otiV.toTryOptionMagicDrawValidationDataResults(p, "invokeDiagramContextMenuActionForSelection", validation)
    } yield result

  }

  def invoke
  (p: Project,
   ev: ActionEvent,
   invocation: InstanceSpecification,
   className: String, methodName: String,
   dpe: DiagramPresentationElement,
   invokeTriggerView: PresentationElement,
   invokeTriggerElement: Element,
   selection: Iterable[PresentationElement])
  : Try[Option[MagicDrawValidationDataResults]]
  = {
    val otiV = OTIMagicDrawValidation(p)

    val dsPlugin = DynamicScriptsPlugin.getInstance
    val metaclassName = StereotypesHelper.getBaseClass(invokeTriggerElement).getName

    val actions = dsPlugin.getRelevantMetaclassActions(
      metaclassName,
      {
        case d: DiagramContextMenuAction =>
          d.className.jname == className && d.methodName.sname == methodName
        case _ =>
          false
      })

    if (actions.size != 1) {
      for {
        vInfo <-
        otiV.constructValidationInfo(
          otiV.MD_OTI_ValidationConstraint_UnresolvedCrossReference,
          Some("Check the instance specification details"),
          Nil)
        validation =
        otiV.makeMDIllegalArgumentExceptionValidation(
          s"*** Ambiguous invocation; there are ${actions.size} relevant dynamic script actions matching the class/method name criteria ***",
          Map(invocation -> List(vInfo)))
        result <-
        otiV.toTryOptionMagicDrawValidationDataResults(p, "invokeDiagramContextMenuActionForSelection", validation)
      } yield result
    }
    else {
      val scripts = actions.head._2
      if (scripts.size != 1) {
        for {
          vInfo <-
          otiV.constructValidationInfo(
            otiV.MD_OTI_ValidationConstraint_UnresolvedCrossReference,
            Some("Check the instance specification details"),
            Nil)
          validation =
          otiV.makeMDIllegalArgumentExceptionValidation(
            s"*** Ambiguous invocation; there are ${actions.size} relevant dynamic script actions matching the class/method name criteria ***",
            Map(invocation -> List(vInfo)))
          result <-
          otiV.toTryOptionMagicDrawValidationDataResults(p, "invokeDiagramContextMenuActionForSelection", validation)
        } yield result
      }
      else scripts.head match {
        case d: DiagramContextMenuAction =>
          System.out.println(s"invokeTriggerView: $invokeTriggerView")
          System.out.println(s"invokeTriggerElement: $invokeTriggerElement")
          System.out.println(s"selection: ${selection.size}")
          selection.foreach { s =>
            System.out.println(s"- selected: $s")
          }
          val action = DynamicDiagramContextMenuActionForTriggerAndSelection(
            p, dpe,
            invokeTriggerView, invokeTriggerElement, selection,
            d, null, null)
          val sm = SessionManager.getInstance
          if (sm.isSessionCreated(p))
            sm.closeSession(p)

          action.actionPerformed(ev)
          Success(None)

        case d: DynamicActionScript =>
          for {
            vInfo <-
            otiV.constructValidationInfo(
              otiV.MD_OTI_ValidationConstraint_UnresolvedCrossReference,
              Some("Check the instance specification details"),
              Nil)
            validation = otiV.makeMDIllegalArgumentExceptionValidation(
              s"*** Invocation error: expected a DiagramContextMenuAction, got: ${d.prettyPrint("  ")}",
              Map(invocation -> List(vInfo)))
            result <-
            otiV.toTryOptionMagicDrawValidationDataResults(p, "invokeDiagramContextMenuActionForSelection", validation)
          } yield result
      }
    }
  }
}