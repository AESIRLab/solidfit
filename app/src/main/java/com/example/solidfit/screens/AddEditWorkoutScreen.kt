package com.example.solidfit.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.rememberAsyncImagePainter
import com.example.solidfit.model.WorkoutItem


@Composable
fun AddEditWorkoutScreen(
    workout: WorkoutItem? = null,
    onSaveWorkout: (String, String, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var id by remember { mutableStateOf(workout?.id ?: "") }
    var name by remember { mutableStateOf(workout?.name ?: "") }
    var duration by remember { mutableStateOf(workout?.duration?: "") }
    var workoutType by remember { mutableStateOf(workout?.workoutType ?: "") }
    var notes by remember {mutableStateOf(workout?.notes ?: "")}
    var mediaUri by remember(workout?.mediaUri) { mutableStateOf(workout?.mediaUri?.let(Uri::parse)?: "")}

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
            mediaUri = it
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Workout Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
        // Active Minutes field
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it },
            label = { Text("Duration (mins)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        // Workout Type field
        OutlinedTextField(
            value = workoutType,
            onValueChange = { workoutType = it },
            label = { Text("Workout Type") },
            modifier = Modifier.fillMaxWidth()
        )

        // Workout Type field
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth()
        )

        mediaUri?.let { uri ->
            Image(
                painter = rememberAsyncImagePainter(model = uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .border(1.5.dp, Color.Gray, RoundedCornerShape(25.dp))
                    .align(Alignment.CenterHorizontally)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Cancel add/edit workout
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                    224f,
                    1f,
                    0.73f)),
                onClick = onCancel
            ) {
                Text("Cancel")
            }
            // Add/Change photo
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                    224f,
                    1f,
                    0.73f)),
                onClick = { mediaLauncher.launch(arrayOf("image/*")) }
            ) {
                Text(if (mediaUri.toString() == "") "Select Photo" else "Change Photo")
            }
            // Save workout
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(
                    224f,
                    1f,
                    0.73f)),
                onClick = {
                    if (name.isNotBlank()) {
                        onSaveWorkout(id, name, duration, workoutType, notes, mediaUri.toString())
                    }
                },
                enabled = name.isNotBlank()

            ) {
                Text("Save")
            }
        }
    }
}
