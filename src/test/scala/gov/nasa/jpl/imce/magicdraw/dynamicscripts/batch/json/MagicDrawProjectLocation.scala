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

import scala.Int
import scala.Predef.{ArrowAssoc, String}

sealed abstract trait MagicDrawProjectLocation {

  def testName: String

}

case class MagicDrawLocalProjectLocation
( localProjectFile: String )
  extends MagicDrawProjectLocation {

  override def testName: String = localProjectFile

}

object MagicDrawLocalProjectLocation {

  implicit val formats
  : Format[MagicDrawLocalProjectLocation]
  = Json.format[MagicDrawLocalProjectLocation]

}

/**
  *
  * @param teamworkServer
  * @param teamworkPort
  * @param teamworkUser
  * @param teamworkPassword
  * @param teamworkProjectPath From MD Open API (TeamworkUtils):
  *                            "MyProject" with no branches =
  *                            "MyProject" "MyProject" branch ["release"] =
  *                            "MyProject##release" "MyProject" branch with subbranch ["release", "sp1"] =
  *                            "MyProject##release##sp1"
  */
case class MagicDrawTeamworkProjectLocation
( teamworkServer: String,
  teamworkPort: Int,
  teamworkUser: String,
  teamworkPassword: String,
  teamworkProjectPath: String)
  extends MagicDrawProjectLocation {

  val server_connection_info = "md://"+teamworkUser+"@"+teamworkServer+":"+teamworkPort+"/'"+teamworkProjectPath+"'"

  override def testName: String = teamworkProjectPath

}

object MagicDrawTeamworkProjectLocation {

  implicit val formats
  : Format[MagicDrawTeamworkProjectLocation]
  = Json.format[MagicDrawTeamworkProjectLocation]

}

object MagicDrawProjectLocation {

  // https://github.com/playframework/playframework/issues/6131
  implicit val formats
  : Format[MagicDrawProjectLocation]
  = {

    val writes
    : Writes[MagicDrawProjectLocation]
    = Writes[MagicDrawProjectLocation] {
      case m: MagicDrawLocalProjectLocation =>
        Json.toJson(m)(MagicDrawLocalProjectLocation.formats).as[JsObject] +
          ("type" -> JsString("MagicDrawLocalProjectLocation"))
      case m: MagicDrawTeamworkProjectLocation =>
        Json.toJson(m)(MagicDrawTeamworkProjectLocation.formats).as[JsObject] +
          ("type" -> JsString("MagicDrawTeamworkProjectLocation"))
    }

    val reads
    : Reads[MagicDrawProjectLocation]
    = Reads[MagicDrawProjectLocation] { json =>
      (json \ "type").validate[String].flatMap {
        case "MagicDrawLocalProjectLocation" =>
          Json.fromJson(json)(MagicDrawLocalProjectLocation.formats)
        case "MagicDrawTeamworkProjectLocation" =>
          Json.fromJson(json)(MagicDrawTeamworkProjectLocation.formats)
      }
    }

    Format(reads,writes)
  }

}