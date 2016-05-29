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

import gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.json.OTIPrimitiveTypes._
import play.json.extra._
import play.api.libs.json._

import scala.Int
import scala.Predef.String
import scalaz._

package object json {

  implicit def taggedStringFormat[T]
  : Format[String @@ T]
  = new Format[String @@ T] {
    def reads(json: JsValue): JsResult[String @@ T] = json match {
      case JsString(v) => JsSuccess(Tag.of[T](v))
      case unknown => JsError(s"String value expected, got: $unknown")
    }

    def writes(v: String @@ T): JsValue = JsString(Tag.unwrap(v))
  }

  implicit val formatMagicDrawProjectLocation
  : Format[MagicDrawProjectLocation]
  = Variants.format[MagicDrawProjectLocation]((__ \ "type").format[String])

  implicit val readsMagicDrawProjectLocation
  : Reads[MagicDrawProjectLocation]
  = Variants.reads[MagicDrawProjectLocation]((__ \ "type").read[String])

  implicit val writesMagicDrawProjectLocation
  : Writes[MagicDrawProjectLocation]
  = Variants.writes[MagicDrawProjectLocation]((__ \ "type").write[String])

  implicit val formatMagicDrawDynamicScript
  : Format[MagicDrawDynamicScript]
  = Variants.format[MagicDrawDynamicScript]((__ \ "type").format[String])

  implicit val readsMagicDrawDynamicScript
  : Reads[MagicDrawDynamicScript]
  = Variants.reads[MagicDrawDynamicScript]((__ \ "type").read[String])

  implicit val writesMagicDrawDynamicScript
  : Writes[MagicDrawDynamicScript]
  = Variants.writes[MagicDrawDynamicScript]((__ \ "type").write[String])

  implicit val formatMagicDrawTestSpec
  : Format[MagicDrawTestSpec]
  = Variants.format[MagicDrawTestSpec]((__ \ "type").format[String])

  implicit val readsMagicDrawTestSpec
  : Reads[MagicDrawTestSpec]
  = Variants.reads[MagicDrawTestSpec]((__ \ "type").read[String])

  implicit val writesMagicDrawTestSpec
  : Writes[MagicDrawTestSpec]
  = Variants.writes[MagicDrawTestSpec]((__ \ "type").write[String])

  implicit val formatOTIArtifactKind
  : Format[OTIArtifactKind]
  = Variants.format[OTIArtifactKind]

  implicit val readsOTIArtifactKind
  : Reads[OTIArtifactKind]
  = Variants.reads[OTIArtifactKind]

  implicit val writesOTIArtifactKind
  : Writes[OTIArtifactKind]
  = Variants.writes[OTIArtifactKind]

}