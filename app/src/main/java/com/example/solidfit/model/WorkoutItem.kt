package com.example.solidfit.model

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.solidfit.WorkoutItemViewModel
import com.zybooks.sksolidannotations.SolidAnnotation
import com.zybooks.soliddaoannotations.SolidDaoAnnotation
import com.zybooks.soliddaoimplannotations.SolidDaoImplAnnotation
import com.zybooks.soliddbannotations.SolidDbAnnotation
import com.zybooks.solidrdsannotations.SolidRemoteDataSource
import com.zybooks.utilities.SolidDefaultTokenStore
import com.zybooks.utilities.SolidDefaultUtilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


//@SolidDefaultTokenStore
//@SolidDefaultUtilities
//@SolidDaoAnnotation
//@SolidDaoImplAnnotation(
//    "http://www.w3.org/2024/ci/core#",
//    "AndroidApplication/SolidFit"
//)
//@SolidDbAnnotation
//@SolidRemoteDataSource
@SolidAnnotation(
    "http://www.w3.org/2024/ci/core#",
    "AndroidApplication/SolidFit"
)

data class WorkoutItem(
    var id: String,
    var name: String = "",
    var dateCreated: Long = System.currentTimeMillis(),
    var dateModified: Long,
    var duration: String,
    var heartRate: Long,
    var workoutType: String = "",
    var notes: String = "",
    var mediaUri: String = ""
)

@Composable
fun WorkoutItem(
    workout: WorkoutItem,
    viewModel: WorkoutItemViewModel,
    onDelete: (WorkoutItem) -> Unit,
    onEdit: (WorkoutItem) -> Unit,
    onSelect: (WorkoutItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onSelect(workout) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column (
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ){
                // NAME
                Text(
                    text = workout.name,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp),
                    maxLines = 1,
                )

                // DURATION
                if (workout.duration.isNotBlank()) {
                    Text(
                        text = buildAnnotatedString {
                            // Doing this style allows for part of the text to be in the 'Medium' bold style while the data text is normal weight
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                // Medium weight
                                append("Duration: ")
                            }
                            // Normal weight
                            append("${workout.duration} minutes")
                        }
                    )
                }

                // WORKOUT TYPE
                if (workout.workoutType.isNotBlank()) {
                    Text(
                        text = buildAnnotatedString {
                            // Doing this style allows for part of the text to be in the 'Medium' bold style while the data text is normal weight
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                // Medium weight
                                append("Workout Type: ")
                            }
                            // Normal weight
                            append(workout.workoutType)
                        }
                    )
                }

                // DATE CREATED & MODIFIED
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                            // Medium weight
                            append("Date: ")
                        }
                        // Normal weight
                        append(
                            SimpleDateFormat("MM/dd/yyyy: hh:mm a", Locale.getDefault()).format(
                                Date(workout.dateCreated)
                            ))
                    }
                )
                if (workout.dateModified != 0.toLong()) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                // Medium weight
                                append("Modified: ")
                            }
                            // Normal weight
                            append(
                                SimpleDateFormat("MM/dd/yyyy: hh:mm a", Locale.getDefault()).format(
                                    Date(workout.dateModified)
                                ))
                        }
                    )
                }

                // NOTES
                if (workout.notes.isNotBlank()) {
                    Text(
                        // Truncates the notes if it's too long
                        maxLines = 3,
                        text = buildAnnotatedString {
                            // Doing this style allows for part of the text to be in the 'Medium' bold style while the data text is normal weight
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                // Medium weight
                                append("Notes: ")
                            }
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 16.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Normal,

                                    )
                            ) {
                                // Smaller, Italicized, Normal-weight font
                                append(workout.notes)
                            }
                        }
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // THUMBNAIL
                if (workout.mediaUri.isNotBlank()) {
                    val ctx = LocalContext.current

                    val model = remember(workout.mediaUri) {
                        val s = workout.mediaUri
                        when {
                            s.isBlank() -> null
                            s.startsWith("content", true) -> Uri.parse(s) // local preview
                            else -> viewModel.buildAuthorizedImageRequest(ctx, s) ?: s
                        }
                    }

                    if (model != null) {
                        // 1. Create ONE painter and remember it
                        val painter = rememberAsyncImagePainter(model = model)

                        // 2. Check the state of THAT painter
                        when (painter.state) {

                            is AsyncImagePainter.State.Loading -> {
                                // The painter is loading, show a spinner
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray.copy(alpha = 0.1f)), // Light background
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }

                            is AsyncImagePainter.State.Success -> {
                                // The painter is successful, show the Image
                                Image(
                                    painter = painter, // <-- Correct: use the existing painter
                                    contentDescription = "Workout photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }

                            is AsyncImagePainter.State.Error -> {
                                // The request failed. Show a "broken image" icon.
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
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

                            is AsyncImagePainter.State.Empty -> {
                                // The model was null or empty.
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray.copy(alpha = 0.1f))
                                )
                            }
                        }
                    } else {
                        // Model is null (VM is not ready), show a spinner
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Gray.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                } else {
                    // This else runs if mediaUri is blank (no image).
                    // We show an empty Box to keep the layout consistent.
                    Box(modifier = Modifier.size(70.dp))
                }

                Row(modifier = Modifier.padding(top = 6.dp)) {
                    // EDIT BUTTON
                    IconButton(onClick = { onEdit(workout) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit workout",
                            tint = Color.Black
                        )
                    }

                    // DELETE BUTTON
                    IconButton(onClick = { onDelete(workout) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete workout",
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    }
}
