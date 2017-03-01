package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.utils

import java.lang.String
import java.io.File

import com.nomagic.magicdraw.core.{Application, Project, StartupParticipant}
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory
import com.nomagic.uml2.ext.magicdraw.auxiliaryconstructs.mdmodels.Model

import scala.Array
import scala.Unit
import scala.Boolean

/**
  * Created by sherzig on 3/1/17.
  */
@scala.deprecated("", "")
class MDUtils {}

object MDUtils {

  /**
    * Launches MagicDraw in "silent mode", enabling a mode in which
    * any potentially required user input is ignored, and all decisions
    * are defaulted.
    *
    * @param args Program arguments for MagicDraw (if any)
    */
  def launchMagicDrawSilently(args : Array[String]) : Unit = Application.getInstance().start(false, true, false, args, null.asInstanceOf[StartupParticipant])

  /**
    * Shuts down MagicDraw.
    */
  def shutdownMagicDraw() : Unit = Application.getInstance.shutdown()

  /**
    * Returns the currently active project.
    *
    * @return The active project if a project is loaded, null otherwise.
    */
  def getActiveProject() : Project = Application.getInstance.getProjectsManager.getActiveProject

  /**
    * Loads a MD project.
    *
    * @param projectFileLocation The location of the project file.
    */
  def loadProject(projectFileLocation : String) : Project = {
    val projectFile = new File(projectFileLocation)
    val var2 = ProjectDescriptorsFactory.createProjectDescriptor(projectFile.toURI)

    Application.getInstance.getProjectsManager.loadProject(var2, true)

    getActiveProject()
  }

  /**
    * Saves the currently active project.
    */
  def saveProject() : Boolean = {
    val projectDescriptor = ProjectDescriptorsFactory.getDescriptorForProject(Application.getInstance.getProjectsManager.getActiveProject)

    Application.getInstance.getProjectsManager.saveProject(projectDescriptor, true)
  }

  /**
    * Closes the currently active project
    */
  def closeActiveProject() : Unit = Application.getInstance.getProjectsManager.closeProject()

  /**
    * Returns the "Model" instance in the currently active project.
    * The model instance is the root element of the model.
    *
    * @return Root model element.
    */
  def getModelInActiveProject() : Model = Application.getInstance.getProjectsManager.getActiveProject.getModel

}