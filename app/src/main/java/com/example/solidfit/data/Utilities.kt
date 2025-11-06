package com.example.solidfit.data

import com.example.solidfit.model.WorkoutItem
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.Resource
import com.hp.hpl.jena.rdf.model.ResourceFactory
import kotlin.String

public class Utilities {
  public companion object {
    public const val ABSOLUTE_URI: String = "AndroidApplication/SolidFit"

    public const val NS_ACP: String = "http://www.w3.org/ns/solid/acp#"

    public const val NS_ACL: String = "http://www.w3.org/ns/auth/acl#"

    public const val NS_LDP: String = "http://www.w3.org/ns/ldp#"

    public const val NS_SKOS: String = "http://www.w3.org/2004/02/skos/core#"

    public const val NS_SOLID: String = "http://www.w3.org/ns/solid/terms#"

    public const val NS_XSD: String = "http://www.w3.org/2001/XMLSchema#"

    public const val NS_WorkoutItem: String = "http://www.w3.org/2024/ci/core#"

    public fun resourceToWorkoutItem(resource: Resource): WorkoutItem {
      val anonModel = ModelFactory.createDefaultModel()
      val id = resource.uri.split("#")[1]

      fun safeGetString(propName: String): String {
        val prop = anonModel.createProperty(NS_WorkoutItem + propName)
        val statement = resource.getProperty(prop)
        return if (statement != null) {
          ResourceFactory.createTypedLiteral(statement.`object`).value.toString().split("^^")[0]
        } else {
          ""
        }
      }

      fun safeGetLong(propName: String): Long {
        val prop = anonModel.createProperty(NS_WorkoutItem + propName)
        val statement = resource.getProperty(prop)
        return if (statement != null) {
          try {
            ResourceFactory.createTypedLiteral(statement.`object`).value.toString().split("^^")[0].toLong()
          } catch (e: NumberFormatException) {
            0L
          }
        } else {
          0L
        }
      }

      val name = safeGetString("name")
      val dateCreated = safeGetLong("dateCreated")
      val dateModified = safeGetLong("dateModified")
      val quantity = safeGetString("quantity")
      val duration = safeGetString("duration")
      val heartRate = safeGetLong("heartRate")
      val workoutType = safeGetString("workoutType")
      val notes = safeGetString("notes")
      val mediaUri = safeGetString("mediaUri")

      return WorkoutItem(id, name, dateCreated, dateModified, quantity, duration, heartRate,
        workoutType, notes, mediaUri)
    }
  }
}
