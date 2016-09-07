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

package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch

import play.api.libs.json._

import scala.StringContext
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

}