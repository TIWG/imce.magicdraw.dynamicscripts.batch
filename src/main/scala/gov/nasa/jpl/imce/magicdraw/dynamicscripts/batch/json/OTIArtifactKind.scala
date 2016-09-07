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

/**
  * A UML Package can be the root of an OTI artifact of some kind.
  * OTI artifact kinds impose well-formedness constraints on
  * UML Package inter-relationships.
  */
sealed trait OTIArtifactKind

/**
  * The kind for a UML Package that is processed as part of an importing/loading an OTI Document
  */
case object OTILoadingArtifactKind
  extends OTIArtifactKind

/**
  * The kind for a UML Package representing a metamodel (built-in or serializable)
  */
sealed trait OTIMetamodelArtifactKind

/**
  * The kind for a UML Package representing a profile (built-in or serializable)
  */
sealed trait OTIProfileArtifactKind

/**
  * The kind for a UML Package representing a model-library (built-in or serializable)
  */
sealed trait OTIModelLibraryArtifactKind

/**
  * A UML Package that is the root of an OTI artifact of some kind
  * that can be serialized to or deserialized from an external representation.
  */
sealed trait OTISerializableArtifactKind

/**
  * The kind for a UML Package representing a metamodel that can be serialized.
  * A metamodel package can acyclically import metamodel or model library packages
  */
case object OTISerializableMetamodelArtifactKind
  extends OTIArtifactKind
    with OTISerializableArtifactKind
    with OTIMetamodelArtifactKind

/**
  * The kind for a UML Package representing a profile that can be serialized.
  * A profile package must acyclically extend at least one metamodel package
  * and can acyclically import model library packages.
  */
case object OTISerializableProfileArtifactKind
  extends OTIArtifactKind
    with OTISerializableArtifactKind
    with OTIProfileArtifactKind

/**
  * The kind for a UML Package representing a model library that can be serialized.
  * A model library package can acyclically import model library packages
  * and can acyclically apply profile packages.
  */
case object OTISerializableModelLibraryArtifactKind
  extends OTIArtifactKind
    with OTISerializableArtifactKind
    with OTIModelLibraryArtifactKind

/**
  * A UML Package that is the root of an OTI artifact of some kind
  * that is built-in an OTI-compatible tool. Such an artifact is neither
  * serialized to nor deserialized from an external representation.
  */
sealed trait OTIBuiltInArtifactKind

/**
  * The kind for a UML Package representing a metamodel that is built-in an OTI compatible tool.
  * A built-in metamodel package can acyclically import built-in metamodel or built-in model library packages
  */
case object OTIBuiltInMetamodelArtifactKind
  extends OTIArtifactKind
    with OTIBuiltInArtifactKind
    with OTIMetamodelArtifactKind

/**
  * The kind for a UML Package representing a profile that is built-in an OTI compatible tool.
  * A built-in profile package must acyclically extend at least one built-in metamodel package
  * and can acyclically import built-in model library packages.
  */
case object OTIBuiltInProfileArtifactKind
  extends OTIArtifactKind
    with OTIBuiltInArtifactKind
    with OTIProfileArtifactKind

/**
  * The kind for a UML Package representing a model library that is built-in an OTI compatible tool.
  * A built-in model library package can acyclically import built-in model library packages
  * and can acyclically apply built-in profile packages.
  */
case object OTIBuiltInModelLibraryArtifactKind
  extends OTIArtifactKind
    with OTIBuiltInArtifactKind
    with OTIModelLibraryArtifactKind

object OTIArtifactKind {

  implicit val formats
  : Format[OTIArtifactKind]
  = Variants.format[OTIArtifactKind]((__ \ "type").format[String])

}