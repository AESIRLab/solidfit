package com.example.solidfit.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

data class ActiveSessionUiState(
    val title: String = "",
    val notes: String = "",
    val isRecording: Boolean = false,
    val startTimeMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val details: SessionDetails = SessionDetails(),
    val avgHeartRate: Long = 0L,
    val hrSamplesCount: Long = 0L,
)

class ActiveSessionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    // HR accumulator for average calculation
    private var hrSum: Long = 0L
    private var hrCount: Long = 0L

    fun startSession(nowMillis: Long = System.currentTimeMillis()) {
        if (_uiState.value.isRecording) return

        hrSum = 0L
        hrCount = 0L

        _uiState.value = _uiState.value.copy(
            isRecording = true,
            startTimeMillis = nowMillis,
            elapsedMillis = 0L,
            avgHeartRate = 0L,
            hrSamplesCount = 0L,
        )

        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val state = _uiState.value
                if (!state.isRecording) break
                val elapsed = max(0L, System.currentTimeMillis() - state.startTimeMillis)
                _uiState.value = state.copy(elapsedMillis = elapsed)
            }
        }
    }

    fun stopSession(): SessionStopResult {
        val state = _uiState.value
        tickerJob?.cancel()
        tickerJob = null

        val durationSeconds = (state.elapsedMillis / 1000L).toInt()

        val avg = if (hrCount > 0L) (hrSum / hrCount) else 0L

        _uiState.value = state.copy(
            isRecording = false,
            avgHeartRate = avg,
            hrSamplesCount = hrCount
        )

        return SessionStopResult(
            title = state.title,
            notes = state.notes,
            durationSeconds = durationSeconds,
            avgHeartRate = avg,
            details = state.details
        )
    }

    fun setTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun setNotes(value: String) {
        _uiState.value = _uiState.value.copy(notes = value)
    }

    fun addExercise(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return

        val current = _uiState.value.details
        val updated = current.copy(
            exercises = current.exercises + ExerciseEntry(name = trimmed, sets = emptyList())
        )
        _uiState.value = _uiState.value.copy(details = updated)
    }

    fun addSet(exerciseIndex: Int, reps: Int, weight: Double?) {
        if (exerciseIndex !in _uiState.value.details.exercises.indices) return
        if (reps <= 0) return

        val current = _uiState.value.details
        val exercises = current.exercises.toMutableList()
        val ex = exercises[exerciseIndex]

        val newSet = SetEntry(
            reps = reps,
            weight = weight,
            timestamp = System.currentTimeMillis()
        )

        exercises[exerciseIndex] = ex.copy(sets = ex.sets + newSet)

        _uiState.value = _uiState.value.copy(details = current.copy(exercises = exercises))
    }

    fun removeExercise(exerciseIndex: Int) {
        val current = _uiState.value.details
        if (exerciseIndex !in current.exercises.indices) return
        val updated = current.copy(exercises = current.exercises.filterIndexed { i, _ -> i != exerciseIndex })
        _uiState.value = _uiState.value.copy(details = updated)
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        val current = _uiState.value.details
        if (exerciseIndex !in current.exercises.indices) return
        val ex = current.exercises[exerciseIndex]
        if (setIndex !in ex.sets.indices) return

        val exercises = current.exercises.toMutableList()
        exercises[exerciseIndex] = ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex })
        _uiState.value = _uiState.value.copy(details = current.copy(exercises = exercises))
    }

    fun onHeartRateSample(bpm: Int) {
        val state = _uiState.value
        if (!state.isRecording) return
        if (bpm <= 0) return

        hrSum += bpm.toLong()
        hrCount += 1L

        val avg = hrSum / hrCount
        _uiState.value = state.copy(
            avgHeartRate = avg,
            hrSamplesCount = hrCount
        )
    }

    override fun onCleared() {
        tickerJob?.cancel()
        tickerJob = null
        super.onCleared()
    }
}

data class SessionStopResult(
    val title: String,
    val notes: String,
    val durationSeconds: Int,
    val avgHeartRate: Long,
    val details: SessionDetails,
)
