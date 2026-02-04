package com.example.solidfit.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.solidfit.WorkoutItemViewModel
import com.example.solidfit.model.WorkoutItem
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.font.FontWeight
import com.example.solidfit.data.session.sessionDetailsFromJson
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import com.example.solidfit.data.session.toJsonString
import com.example.solidfit.session.SessionDetails
import com.example.solidfit.session.ExerciseEntry
import com.example.solidfit.session.SetEntry

@Composable
fun AddEditWorkoutScreen(
    workout: WorkoutItem? = null,
    viewModel: WorkoutItemViewModel,
    onSaveWorkout: (String, String, String, String, String, Long, String, String, String) -> Unit,
    onCancel: () -> Unit,
    onStartSession: () -> Unit
    ) {
    var id by remember { mutableStateOf(workout?.id ?: "") }
    var name by remember { mutableStateOf(workout?.name ?: "") }
    var quantity by remember { mutableStateOf(workout?.quantity?: "") }
    var duration by remember { mutableStateOf(workout?.duration?: "") }
    var workoutType by remember { mutableStateOf(workout?.workoutType ?: "") }
    var datePerformed by remember { mutableLongStateOf(workout?.datePerformed ?: System.currentTimeMillis()) }
    var notes by remember {mutableStateOf(workout?.notes ?: "") }
    var mediaUri by remember { mutableStateOf(workout?.mediaUri ?: "") }
    var detailsJson by remember { mutableStateOf(workout?.detailsJson ?: "") }
    val isSession = detailsJson.isNotBlank()

    // Editable in-UI model (sessions only)
    val exercises = remember(detailsJson) {
        val parsed = runCatching { sessionDetailsFromJson(detailsJson) }.getOrNull()
        // start empty if parsing fails
        mutableStateOf(parsed?.exercises ?: emptyList())
    }

    LaunchedEffect(workout?.mediaUri, workout?.dateModified) {
        mediaUri = workout?.mediaUri ?: ""
    }

    val formatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

    val context = LocalContext.current
    // Used to display image
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            mediaUri = it.toString()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        if (workout == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Want to record a session?", fontWeight = FontWeight.Bold)
                    Text("Use session recording to add exercises + sets automatically.")
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onStartSession,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start Session") }
                }
            }
        }

        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Workout Name (Required)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )


        // Workout Type field
        if (!isSession) {
            OutlinedTextField(
                value = workoutType,
                onValueChange = { workoutType = it },
                label = { Text("Workout Type") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Quantity field
        if (!isSession) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Duration field
        if (!isSession) {
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duration (mins)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Notes field
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth()
        )

        if (isSession) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Exercises", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))

                    // Add exercise button
                    Button(
                        onClick = {
                            exercises.value += ExerciseEntry(name = "", sets = emptyList())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Exercise")
                    }

                    Spacer(Modifier.height(12.dp))

                    exercises.value.forEachIndexed { exIndex, ex ->
                        ExerciseEditorCard(
                            exercise = ex,
                            onChangeName = { newName ->
                                exercises.value = exercises.value.mapIndexed { i, e ->
                                    if (i == exIndex) e.copy(name = newName) else e
                                }
                            },
                            onRemoveExercise = {
                                exercises.value = exercises.value.filterIndexed { i, _ -> i != exIndex }
                            },
                            onAddSet = {
                                exercises.value = exercises.value.mapIndexed { i, e ->
                                    if (i == exIndex) e.copy(sets = e.sets + SetEntry(reps = 0, weight = null, timestamp = System.currentTimeMillis()))
                                    else e
                                }
                            },
                            onUpdateSet = { setIndex, reps, weight ->
                                exercises.value = exercises.value.mapIndexed { i, e ->
                                    if (i != exIndex) e else {
                                        val newSets = e.sets.mapIndexed { si, s ->
                                            if (si == setIndex) s.copy(reps = reps, weight = weight) else s
                                        }
                                        e.copy(sets = newSets)
                                    }
                                }
                            },
                            onRemoveSet = { setIndex ->
                                exercises.value = exercises.value.mapIndexed { i, e ->
                                    if (i != exIndex) e else e.copy(sets = e.sets.filterIndexed { si, _ -> si != setIndex })
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        if (mediaUri.isNotBlank()) {
            val ctx = LocalContext.current

            val model = remember(mediaUri) {
                val s = mediaUri
                when {
                    s.isBlank() -> null
                    s.startsWith("content", true) -> Uri.parse(s)
                    else -> viewModel.buildAuthorizedImageRequest(ctx, s) ?: s
                }
            }

            if (model != null) {
                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = "Workout photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .border(1.5.dp, Color.Gray, RoundedCornerShape(25.dp))
                        .align(Alignment.CenterHorizontally)
                ) {
                    // This logic is identical to your WorkoutCardScreen
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Gray.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        }
                        is AsyncImagePainter.State.Error -> {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Gray.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Failed to load image",
                                    tint = Color.Gray
                                )
                            }
                        }
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
        }

        val imageIsSelected = mediaUri.isNotBlank()

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Add/Change photo button
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                    224f,
                    1f,
                    0.73f)),
                onClick = { mediaLauncher.launch(arrayOf("image/*")) }
            ) {
                Text(if (mediaUri == "") "Select Photo" else "Change Photo")
            }

            // Remove button
            if (imageIsSelected) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                        224f,
                        1f,
                        0.73f)),
                    onClick = {
                        mediaUri = ""
                    }
                ) {
                    Text("Remove")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Cancel add/edit workout button
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                    224f,
                    1f,
                    0.73f)),
                onClick = onCancel
            ) {
                Text("Cancel")
            }

            // Save workout button
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                    224f,
                    1f,
                    0.73f)),
                onClick = {
                    val detailsJsonToSave =
                        if (isSession) {
                            val cleaned = exercises.value
                                .filter { it.name.isNotBlank() } // optional: drop empty exercise names
                                .map { ex ->
                                    ex.copy(
                                        sets = ex.sets.filter { it.reps > 0 } // optional: drop invalid sets
                                    )
                                }

                            SessionDetails(exercises = cleaned).toJsonString()
                        } else {
                            ""
                        }

                    if (name.isNotBlank()) {
                        onSaveWorkout(
                            id, name, quantity, duration, workoutType, datePerformed, notes, mediaUri,
                            detailsJsonToSave
                        )
                    }
                },
                enabled = name.isNotBlank()

            ) {
                Text("Save")
            }
        }
    }
}
@Composable
private fun ExerciseEditorCard(
    exercise: ExerciseEntry,
    onChangeName: (String) -> Unit,
    onRemoveExercise: () -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (setIndex: Int, reps: Int, weight: Double?) -> Unit,
    onRemoveSet: (setIndex: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = exercise.name,
                    onValueChange = onChangeName,
                    label = { Text("Exercise name") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRemoveExercise) {
                    Text("Remove")
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Set") }

            Spacer(Modifier.height(10.dp))

            exercise.sets.forEachIndexed { setIndex, set ->
                SetEditorRow(
                    setNumber = setIndex + 1,
                    repsInitial = set.reps,
                    weightInitial = set.weight,
                    onChange = { reps, weight -> onUpdateSet(setIndex, reps, weight) },
                    onRemove = { onRemoveSet(setIndex) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SetEditorRow(
    setNumber: Int,
    repsInitial: Int,
    weightInitial: Double?,
    onChange: (reps: Int, weight: Double?) -> Unit,
    onRemove: () -> Unit
) {
    var repsText by remember(repsInitial) { mutableStateOf(if (repsInitial == 0) "" else repsInitial.toString()) }
    var weightText by remember(weightInitial) { mutableStateOf(weightInitial?.let { stripTrailingZero(it) } ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Set $setNumber", modifier = Modifier.width(56.dp))

        Spacer(Modifier.width(8.dp))

        OutlinedTextField(
            value = repsText,
            onValueChange = {
                repsText = it
                val reps = it.toIntOrNull() ?: 0
                val weight = weightText.toDoubleOrNull()
                onChange(reps, weight)
            },
            label = { Text("Reps") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                val reps = repsText.toIntOrNull() ?: 0
                val weight = it.toDoubleOrNull()
                onChange(reps, weight)
            },
            label = { Text("Weight (opt)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        TextButton(onClick = onRemove) { Text("X") }
    }
}

private fun stripTrailingZero(v: Double): String {
    val i = v.toInt()
    return if (v == i.toDouble()) i.toString() else v.toString()
}

