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

package gov.nasa.jpl.imce.magicdraw.dynamicscripts.batch.helpers

import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.{InstanceSpecification, ValueSpecification}

import scala.collection.JavaConversions._
import scala.collection.immutable.{::,List,Nil}
import scala.{AnyVal,Option,None,Some}
import scala.Predef.{require,String}

class InstanceSpecificationHelper
(@scala.transient val self: InstanceSpecification)
extends AnyVal {

  def getValuesOfFeatureSlot
  ( featureName: String )
  : Option[List[ValueSpecification]]
  = {
    ( for {
      slot <- self.getSlot
      f <- Option.apply(slot.getDefiningFeature)
      fName = f.getName
      if fName == featureName
    } yield slot ).toList match {
      case Nil =>
        None
      case s :: sx =>
        require( sx == Nil )
        Some( s.getValue.to[List] )
    }
  }
}