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

package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.json

import play.api.libs.json._
import play.json.extra._

import scala.Predef.String

sealed abstract trait MagicDrawDynamicScript {
  val className: String
  val methodName: String

  def testName: String

}

case class InvokeToolbarMenu
( override val className: String,
  override val methodName: String)
  extends MagicDrawDynamicScript {

  override def testName: String =
    "InvokeToolbarMenu(" + className + ", " + methodName + ")"

}

case class InvokeDiagramContextMenuActionForSelection
( diagramID: String,
  instanceSpecificationID: String )
  extends MagicDrawDynamicScript {

  override val className
  : String
  = "gov.nasa.jpl.imce.oti.magicdraw.dynamicScripts.invokeDiagramContextMenuActionForSelection"

  override val methodName
  : String
  = "doit"

  override def testName
  : String
  = "InvokeDiagramContextMenu(instanceSpecification=" + instanceSpecificationID + ")"

}

object MagicDrawDynamicScript {

  implicit val formats
  : Format[MagicDrawDynamicScript]
  = Variants.format[MagicDrawDynamicScript]((__ \ "type").format[String])

}