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

package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.validation

import java.io.{File, FileOutputStream, OutputStreamWriter, PrintWriter}

import com.nomagic.magicdraw.annotation.Annotation
import com.nomagic.magicdraw.core.Application
import gov.nasa.jpl.dynamicScripts.magicdraw.utils.MDUML
import gov.nasa.jpl.dynamicScripts.magicdraw.validation.MagicDrawValidationDataResults

import scala.collection.JavaConversions._
import scala.util._
import scala.xml._
import scala.{Boolean,Option,None,Some,StringContext,Unit}

case class ExportAsJUnitResult()
  extends MagicDrawValidationDataResults.ValidationAnnotationAction(
    "Export Validation Result as JUnit Result",
    "Export Validation Result as JUnit Result" ) {

  def canExecute
  ( annotation: Annotation )
  : Boolean
  = true
   
  def execute
  ( annotation: Annotation )
  : Unit
  = ()

  override def execute
  ( annotations: java.util.Collection[Annotation] )
  : Unit
  = {

    val app = Application.getInstance()
    val guiLog = app.getGUILog

    ExportAsJUnitResult.saveAnnotationsAsJUnitResult(annotations) match {
      case Failure(f) =>
        guiLog.showError("Failed to save Annotations as JUnit Results", f)
      case Success(None) =>
        guiLog.log("Cancelled exporting of Annotations as JUnit results")
      case Success(Some(f)) =>
        guiLog.log(s"Exported Annotatations as JUnit result file: ${f.getAbsolutePath}")
    }
  }

  
}

object ExportAsJUnitResult {

  def saveAnnotationsAsJUnitResult
  ( annotations: java.util.Collection[Annotation] )
  : Try[Option[File]]
  = MDUML.saveFile(
      "Exporting Annotations as JUnit results",
      s"${annotations.size} annotations",
      ".xml") match {
      case Failure(f) =>
        Failure(f)
      case Success(None) =>
        Success(None)
      case Success(Some(xmlFile)) =>
        val app = Application.getInstance()
        val guiLog = app.getGUILog

        guiLog.log(s"Export Annotatations as JUnit result file: ${xmlFile.getAbsolutePath}")

        val annotationTestSuites = for {
          (element, as) <- annotations.groupBy { a => a.getTarget }
          testcases = as map { a =>
            Elem(
              prefix=null,
              label="testcase",
              attributes=
                new UnprefixedAttribute(key = "message", value = s"${a.getText}", Null),
              scope=TopScope,
              minimizeEmpty=true)
          }
        } yield
          Elem(
            prefix=null,
            label="testsuite",
            attributes=
              new UnprefixedAttribute(key = "package", value = s"${element.getHumanName} (ID=${element.getID})", Null),
            scope=TopScope,
            minimizeEmpty=true,
            testcases.toSeq: _*)

        val results = Elem(
          prefix = null,
          label = "testsuites",
          attributes = new UnprefixedAttribute(key = "tests", value = annotations.size.toString, Null),
          scope = TopScope,
          minimizeEmpty = true,
          annotationTestSuites.toSeq: _*)

        val xmlPrettyPrinter = new PrettyPrinter(width = 300, step = 2)
        val xmlOutput = xmlPrettyPrinter.format(results)
        val bw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(xmlFile), "UTF-8"))
        bw.println("<?xml version='1.0' encoding='UTF-8'?>")
        bw.println(xmlOutput)
        bw.close()
        Success(None)
    }

}