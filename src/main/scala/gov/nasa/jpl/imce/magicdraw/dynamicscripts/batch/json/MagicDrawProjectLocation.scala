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

import scala.{Int,Option}
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
  teamworkUser: Option[String],
  teamworkPassword: Option[String],
  teamworkProjectPath: String)
  extends MagicDrawProjectLocation {

  val server_connection_info = "md://"+teamworkServer+":"+teamworkPort+"/'"+teamworkProjectPath+"'"

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