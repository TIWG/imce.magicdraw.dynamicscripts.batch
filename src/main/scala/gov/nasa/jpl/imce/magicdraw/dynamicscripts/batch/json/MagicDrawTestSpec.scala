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
package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.json

import play.api.libs.json._
import play.json.extra._

import scala.collection.immutable._
import scala.{None, Option, Some}
import scala.Predef.String

sealed abstract trait MagicDrawTestSpec {

  val requiredPlugins: List[String]
  val dynamicScriptFiles: List[String]
  def testName: String
  def dynamicScriptClass: String
  def dynamicScriptMethod: String
  val projectLocation: Option[MagicDrawProjectLocation]
  val testScript: MagicDrawDynamicScript

  def getProjectLocationTestName
  : String
  = projectLocation match {
    case None =>
      "No ProjectLocation"
    case Some(loc) =>
      loc.testName
  }
}

case class SimpleMagicDrawTestSpec
( override val requiredPlugins: List[String],
  override val dynamicScriptFiles: List[String],
  override val projectLocation: Option[MagicDrawProjectLocation],
  override val testScript: MagicDrawDynamicScript )
  extends MagicDrawTestSpec {

  override def testName
  : String
  = testScript.testName + " on " + getProjectLocationTestName

  override def dynamicScriptClass
  : String
  = testScript.className

  override def dynamicScriptMethod
  : String
  = testScript.methodName

}

object MagicDrawTestSpec {

  implicit val formats
  : Format[MagicDrawTestSpec]
  = Variants.format[MagicDrawTestSpec]((__ \ "type").format[String])

}