package com.example.solidfit.session

data class SessionDetails(
    val exercises: List<ExerciseEntry> = emptyList()
)

data class ExerciseEntry(
    val name: String = "",
    val sets: List<SetEntry> = emptyList()
)

data class SetEntry(
    val reps: Int = 0,
    val weight: Double? = null, // optional weight
    val timestamp: Long = System.currentTimeMillis()
)
