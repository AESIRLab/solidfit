package com.example.solidfit.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import coil.compose.rememberAsyncImagePainter
import com.example.solidfit.WorkoutItemViewModel
import com.example.solidfit.model.WorkoutItem


@Composable
fun AddEditWorkoutScreen(
    workout: WorkoutItem? = null,
    viewModel: WorkoutItemViewModel,
    onSaveWorkout: (String, String, String, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var id by remember { mutableStateOf(workout?.id ?: "") }
    var name by remember { mutableStateOf(workout?.name ?: "") }
    var quantity by remember { mutableStateOf(workout?.quantity?: "")}
    var duration by remember { mutableStateOf(workout?.duration?: "") }
    var workoutType by remember { mutableStateOf(workout?.workoutType ?: "") }
    var notes by remember {mutableStateOf(workout?.notes ?: "")}
    var mediaUri by remember { mutableStateOf(workout?.mediaUri ?: "") }

    LaunchedEffect(workout?.mediaUri, workout?.dateModified) {
        mediaUri = workout?.mediaUri ?: ""
    }

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
        OutlinedTextField(
            value = workoutType,
            onValueChange = { workoutType = it },
            label = { Text("Workout Type") },
            modifier = Modifier.fillMaxWidth()
        )

        // Quantity field
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it },
            label = { Text("Quantity") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Duration field
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it },
            label = { Text("Duration (mins)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Notes field
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth()
        )

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
                Text(if (mediaUri.toString() == "") "Select Photo" else "Change Photo")
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
                    if (name.isNotBlank()) {
                        onSaveWorkout(id, name, quantity, duration, workoutType, notes, mediaUri)
                    }
                },
                enabled = name.isNotBlank()

            ) {
                Text("Save")
            }
        }
    }
}
