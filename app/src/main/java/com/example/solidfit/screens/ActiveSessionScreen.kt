package com.example.solidfit.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.solidfit.healthdata.InputReadingsViewModel
import com.example.solidfit.session.ActiveSessionViewModel
import com.example.solidfit.data.session.toJsonString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    heartSharedVm: InputReadingsViewModel,
    onStopAndSave: (
        title: String,
        notes: String,
        durationMinutes: Int,
        avgHeartRate: Long,
        detailsJson: String
    ) -> Unit,
    onCancel: () -> Unit,
    sessionVm: ActiveSessionViewModel = viewModel()
) {
    val uiState by sessionVm.uiState.collectAsState()

    // Start timer immediately on entry (only once)
    LaunchedEffect(Unit) {
        sessionVm.startSession()
    }

    // Automatically starts search for bluetooth heart rate devices
    LaunchedEffect(Unit) {
        sessionVm.startSession()
        heartSharedVm.startBleScan()
    }

    // Watch current BPM and accumulate average while recording
    val bpm by heartSharedVm.currentBpmState
    LaunchedEffect(bpm, uiState.isRecording) {
        val value = bpm
        if (uiState.isRecording && value != null && value > 0) {
            sessionVm.onHeartRateSample(value)
        }
    }

    var newExerciseName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Active Session")

        Spacer(Modifier.height(12.dp))

        // Timer + Avg HR
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Time: ${formatElapsed(uiState.elapsedMillis)}")
            Text(text = "Avg HR: ${if (uiState.avgHeartRate > 0) uiState.avgHeartRate else "-"}")
        }

        // --- Heart Rate connection card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // Use State version if you added it
                val bpm by heartSharedVm.currentBpmState

                Text(
                    text = "Heart Rate: ${bpm?.toString() ?: "Not connected"}"
                )

                Spacer(Modifier.height(8.dp))

                Row {
                    Button(onClick = { heartSharedVm.startBleScan() }) {
                        Text("Scan for devices")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (heartSharedVm.devices.isEmpty()) {
                    Text(text = "No devices found.")
                } else {
                    heartSharedVm.devices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = device.name ?: device.address)
                            Button(onClick = { heartSharedVm.connectTo(device) }) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
// --- End Heart Rate card ---


        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.title,
            onValueChange = { sessionVm.setTitle(it) },
            label = { Text("Title (optional)") }
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.notes,
            onValueChange = { sessionVm.setNotes(it) },
            label = { Text("Notes (optional)") }
        )

        Spacer(Modifier.height(16.dp))

        // Add exercise
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = newExerciseName,
                onValueChange = { newExerciseName = it },
                label = { Text("New exercise name") }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    sessionVm.addExercise(newExerciseName)
                    newExerciseName = ""
                }
            ) { Text("Add") }
        }

        Spacer(Modifier.height(12.dp))

        // Exercises list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(uiState.details.exercises) { exIndex, ex ->
                ExerciseCard(
                    exerciseName = ex.name,
                    setsCount = ex.sets.size,
                    onAddSet = { reps, weight ->
                        sessionVm.addSet(exIndex, reps, weight)
                    },
                    onRemoveExercise = { sessionVm.removeExercise(exIndex) },
                    setsContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ex.sets.forEachIndexed { setIndex, set ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val weightText = set.weight?.let { " @ ${it}" } ?: ""
                                    Text(text = "Set ${setIndex + 1}: ${set.reps} reps$weightText")
                                    TextButton(onClick = { sessionVm.removeSet(exIndex, setIndex) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val hasAtLeastOneSet = uiState.details.exercises.any { it.sets.isNotEmpty() }

        // Stop/Cancel
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Cancel") }

            if (!hasAtLeastOneSet) {
                Text(
                    text = "Add at least one set to save.",
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 14.sp
                )
            }

            Button(
                enabled = hasAtLeastOneSet,
                onClick = {
                    val result = sessionVm.stopSession()
                    //TODO: TESTING-change int to change KB size of workout item
                    val massiveNotes = generateDummyPayload(2)
                    val detailsJson = result.details.toJsonString()
                    onStopAndSave(
                        result.title,
                        massiveNotes,
//                        result.notes,
                        result.durationSeconds,
                        result.avgHeartRate,
                        detailsJson
                    )
                }
            ) { Text("Stop & Save") }
        }
    }
}

@Composable
private fun ExerciseCard(
    exerciseName: String,
    setsCount: Int,
    onAddSet: (reps: Int, weight: Double?) -> Unit,
    onRemoveExercise: () -> Unit,
    setsContent: @Composable () -> Unit
) {
    var repsText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = exerciseName)
                TextButton(onClick = onRemoveExercise) { Text("Remove") }
            }

            Spacer(Modifier.height(6.dp))
            Text(text = "Sets: $setsCount")
            Spacer(Modifier.height(10.dp))

            // Add set inputs (reps + optional weight)
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val reps = repsText.toIntOrNull()
                        val weight = weightText.toDoubleOrNull() // null if blank/invalid

                        if (reps != null && reps > 0) {
                            onAddSet(reps, weight)
                            repsText = ""
                            weightText = ""
                        }
                    }
                ) { Text("Add Set") }
            }

            Spacer(Modifier.height(10.dp))

            setsContent()
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun generateDummyPayload(targetKilobytes: Int): String {
    val bytesNeeded = targetKilobytes * 1024
    // Creates a string of "A"s exactly that many bytes long
    return "A".repeat(bytesNeeded)
}