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
package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.validation

import java.io.{File, FileOutputStream, OutputStreamWriter, PrintWriter}

import com.nomagic.magicdraw.annotation.Annotation
import com.nomagic.magicdraw.core.Application
import gov.nasa.jpl.dynamicScripts.magicdraw.utils.MDUML
import gov.nasa.jpl.dynamicScripts.magicdraw.validation.MagicDrawValidationDataResults

import scala.collection.JavaConversions._
import scala.language.postfixOps
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