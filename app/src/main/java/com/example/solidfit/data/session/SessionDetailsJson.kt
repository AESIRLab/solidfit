package com.example.solidfit.data.session

import com.example.solidfit.session.ExerciseEntry
import com.example.solidfit.session.SessionDetails
import com.example.solidfit.session.SetEntry
import org.json.JSONArray
import org.json.JSONObject

fun SessionDetails.toJsonString(): String {
    val root = JSONObject()
    val exArr = JSONArray()
    exercises.forEach { ex ->
        val exObj = JSONObject()
        exObj.put("name", ex.name)

        val setsArr = JSONArray()
        ex.sets.forEach { s ->
            val sObj = JSONObject()
            sObj.put("reps", s.reps)
            if (s.weight != null) sObj.put("weight", s.weight)
            sObj.put("timestamp", s.timestamp)
            setsArr.put(sObj)
        }

        exObj.put("sets", setsArr)
        exArr.put(exObj)
    }
    root.put("exercises", exArr)
    return root.toString()
}

fun sessionDetailsFromJson(json: String): SessionDetails {
    if (json.isBlank()) return SessionDetails()

    val root = JSONObject(json)
    val exArr = root.optJSONArray("exercises") ?: JSONArray()

    val exercises = buildList {
        for (i in 0 until exArr.length()) {
            val exObj = exArr.getJSONObject(i)
            val name = exObj.optString("name", "")

            val setsArr = exObj.optJSONArray("sets") ?: JSONArray()
            val sets = buildList {
                for (j in 0 until setsArr.length()) {
                    val sObj = setsArr.getJSONObject(j)
                    val hasWeight = sObj.has("weight") && !sObj.isNull("weight")
                    add(
                        SetEntry(
                            reps = sObj.optInt("reps", 0),
                            weight = if (hasWeight) sObj.optDouble("weight") else null,
                            timestamp = sObj.optLong("timestamp", 0L)
                        )
                    )
                }
            }

            add(ExerciseEntry(name = name, sets = sets))
        }
    }

    return SessionDetails(exercises = exercises)
}
