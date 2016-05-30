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