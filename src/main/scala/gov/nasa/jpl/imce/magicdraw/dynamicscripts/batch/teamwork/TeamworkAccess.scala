/*
 * Copyright 2016 California Institute of Technology ("Caltech").
 * U.S. Government sponsorship acknowledged.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * License Terms
 */

package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.teamwork

import java.awt.event.ActionEvent
import java.lang.System

import com.nomagic.magicdraw.core.{Application, Project}
import com.nomagic.magicdraw.core.project.{ProjectDescriptor, ProjectsManager}
import com.nomagic.magicdraw.teamwork.application.TeamworkUtils
import gov.nasa.jpl.dynamicScripts.DynamicScriptsTypes.MainToolbarMenuAction
import gov.nasa.jpl.dynamicScripts.magicdraw.validation.MagicDrawValidationDataResults

import scala.collection.immutable._
import scala.{None, Option, Some, StringContext, Unit}
import scala.swing._
import scala.swing.event.ButtonClicked
import scala.util.control.Exception._
import scala.util.{Success, Try}
import scala.Predef.{augmentString, wrapCharArray, String}

object TeamworkAccess {

  def teamworkLogin
  ( p: Project, ev: ActionEvent, script: MainToolbarMenuAction )
    : Try[Option[MagicDrawValidationDataResults]]
  = {

    val dialog = new Dialog {

      title = "Enter MagicDraw Teamwork Login Credentials"
      val panel = new GridPanel(6, 2) {

        val addressLabel = new Label("Teamwork server")
        val addressField = new TextField(columns = 30)

        val portLabel = new Label("Teamwork port")
        val portField = new TextField(columns = 5)

        val userLabel = new Label("User")
        val userField = new TextField(columns = 30)

        val passwordLabel = new Label("Password")
        val passwordField = new PasswordField(columns = 30)

        val projectLabel = new Label("Project path")
        val projectField = new TextField(columns = 60)

        val cancel = new Button("Cancel")
        val ok = new Button("Ok")

        val buttonGroup = new ButtonGroup(cancel, ok)

        contents ++=
          addressLabel :: addressField ::
          portLabel :: portField ::
          userLabel :: userField ::
          passwordLabel :: passwordField ::
          projectLabel :: projectField ::
          cancel :: ok ::
          Nil

        listenTo(cancel, ok)

        reactions += {
          case ButtonClicked(`ok`) =>

            val teamworkServer = addressField.text
            val teamworkPort = portField.text.toInt
            val secure = true

            val teamworkUser = userField.text
            val teamworkPassword = passwordField.password.mkString

            val teamworkProjectPath = projectField.text

            val server_connection_info = "md://"+teamworkUser+"@"+teamworkServer+":"+teamworkPort+"/'"+teamworkProjectPath+"'"

            Option
              .apply(TeamworkUtils.loginWithSession(teamworkServer, teamworkPort, teamworkUser, teamworkPassword)) match {
              case None =>
                System.out.println("Cannot login on Teamwork server: " + server_connection_info)

              case Some(s) =>
                System.out.println("successful server login for: " + server_connection_info)

                nonFatalCatch[Unit]
                  .withApply { (t: java.lang.Throwable) =>
                    System.out.println(t.getMessage)
                  }
                  .apply {
                    val pDescriptor = TeamworkUtils.getRemoteProjectDescriptorByQualifiedName(teamworkProjectPath)
                    System.out.println("successfully retrieved the descriptor for: " + server_connection_info)

                    val p = loadTeamworkProject(server_connection_info, pDescriptor)
                    Application.getInstance.getProjectsManager.setActiveProject(p)

                    System.out.println(s"active project = $p")
                  }
            }

            close()

          case ButtonClicked(`cancel`) =>
            System.out.println(s"Cancel...")
            close()
        }
      }

      contents = panel

    }

    dialog.open()

    System.out.println(s"Done...")

    Success(None)
  }


  def loadTeamworkProject(server_connection_info: String, descriptor: ProjectDescriptor)
  : Project
  = {
    val a = Application.getInstance
    val pManager: ProjectsManager = a.getProjectsManager
    val silent = true
    pManager.loadProject(descriptor, silent)

    Option.apply(a.getProject) match {
      case None =>
        throw new java.lang.IllegalArgumentException("Failed to load project: " + server_connection_info)

      case Some(p) =>
        System.out.println("successfully opened teamwork project: " + server_connection_info)
        p
    }
  }
}