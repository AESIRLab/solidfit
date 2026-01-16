package com.example.solidfit.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.solidfit.WorkoutItemViewModel
import com.example.solidfit.model.WorkoutItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun WorkoutCard(
    workout: WorkoutItem,
    viewModel: WorkoutItemViewModel
) {
    Box (
        modifier = Modifier
            .fillMaxHeight()
    ) {
        Column (
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ){
            if (workout.mediaUri.isNotBlank()) {
                val ctx = LocalContext.current
                val model = remember(workout.mediaUri, workout.dateModified) {
                    val s = workout.mediaUri
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
                            .padding(top = 16.dp)
                            .fillMaxWidth(0.8f)
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .align(Alignment.CenterHorizontally)
                            .border(.7.dp, Color.Black, RoundedCornerShape(8.dp))
                    ) {
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
                } else {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(alpha = 0.1f))
                    )
                }
            } else {
                Box(modifier = Modifier.size(70.dp))
            }


            // NAME
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
            ) {
                Text(
                    text = "Name:",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(110.dp)
                )
                Text(
                    text = workout.name,
                    fontSize = 18.sp,
                    textDecoration = TextDecoration.Underline
                )
            }

            // QUANTITY
            if (workout.quantity.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Quantity:",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(110.dp)
                    )
                    Text(
                        text = workout.quantity,
                        fontSize = 18.sp
                    )
                }
            }

            // DURATION
            if (workout.duration.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Duration:",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(110.dp)
                    )
                    Text(
                        text = "${workout.duration} minutes",
                        fontSize = 18.sp
                    )
                }
            }

            // DATE CREATED, PERFORMED, & MODIFIED
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Created:",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(110.dp)
                )
                Text(
                    text = SimpleDateFormat("MM/dd/yyyy: hh:mm a", Locale.getDefault()).format(
                        Date(workout.dateCreated)),
                    fontSize = 18.sp
                )
            }

            if (workout.datePerformed != 0L) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Performed:",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(110.dp)
                    )
                    Text(
                        text = SimpleDateFormat("MM/dd/yyyy: hh:mm a", Locale.getDefault()).format(
                            Date(workout.datePerformed)),
                        fontSize = 18.sp
                    )
                }
            }
            if (workout.dateModified != workout.dateCreated) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Modified:",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(110.dp)
                    )
                    Text(
                        text = SimpleDateFormat("MM/dd/yyyy: hh:mm a", Locale.getDefault()).format(
                            Date(workout.dateModified)),
                        fontSize = 18.sp
                    )
                }
            }
            // NOTES
            if (workout.notes.isNotEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        // Doing this style allows for part of the text to be in the 'Medium' bold style while the data text is normal weight
                        withStyle(style = SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium)) {
                            // Medium weight
                            append("Description:")
                        }
                    },
                )
                Text(
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    text = buildAnnotatedString {
                        // Doubled "withStyle" so I could add lineHeight to only the notes body
                        withStyle(style = ParagraphStyle(lineHeight = 30.sp)) {
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                // Smaller, Italicized, Normal-weight font
                                append("\t\t\t${workout.notes}")
                            }
                        }
                    }
                )
            }
        }
    }
}