package com.example.solidfit.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.solidfit.WorkoutItemViewModel
import com.example.solidfit.buildResourceDPoP
import com.example.solidfit.model.WorkoutItem
import kotlinx.coroutines.flow.firstOrNull
import org.skCompiler.generatedModel.AuthTokenStore
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
                .padding(start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState())
        ){
            if (workout.mediaUri.isNotBlank()) {
                val ctx = LocalContext.current
                val model = remember(workout.mediaUri) {
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
            Text(
                modifier = Modifier.padding(top = 18.dp, bottom = 5.dp),
                text = buildAnnotatedString {
                    // Doing this style allows for part of the text to be in the 'Medium' bold style while the data text is normal weight
                    withStyle(style = SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium)) {
                        // Medium weight
                        append("Name:\t\t\t\t\t\t")
                    }
                    withStyle(
                        style = SpanStyle(
                            fontSize = 18.sp
                        )
                    ) {
                        // Normal weight
                        append(workout.name)
                    }
                }
            )

            // DURATION
            Text(
                modifier = Modifier.padding(bottom = 5.dp),
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium)) {
                        // Medium weight
                        append("Duration:\t\t\t")
                    }
                    withStyle(
                        style = SpanStyle(
                            fontSize = 18.sp
                        )
                    ) {
                        // Normal weight
                        append("${workout.duration} minutes")
                    }
                }
            )

            // DATE
            Text(
                modifier = Modifier.padding(bottom = 5.dp),
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium)) {
                        // Medium weight
                        append("Date:\t\t\t\t\t\t\t")
                    }
                    withStyle(
                        style = SpanStyle(
                            fontSize = 18.sp,
                        )
                    ) {
                        // Normal weight
                        append(
                            SimpleDateFormat("MM/dd/yyyy: hh:mm a", Locale.getDefault()).format(
                                Date(workout.dateCreated)
                            )
                        )
                    }
                }
            )
            // DESCRIPTION
            if (workout.notes != "") {
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