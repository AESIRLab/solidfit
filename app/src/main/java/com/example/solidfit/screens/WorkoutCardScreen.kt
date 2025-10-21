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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.solidfit.model.WorkoutItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun WorkoutCard(
    workout: WorkoutItem
) {
    Box (
        modifier = Modifier
            .background(Color(0xffEBDCD1))
            .fillMaxHeight()
    ) {
        Column (
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState())
        ){
            // IMAGE
            if (workout.mediaUri.isNotBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(workout.mediaUri)),
                    contentDescription = "Workout photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(0.8f)
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .align(Alignment.CenterHorizontally)
                        .border(.7.dp, Color.Black, RoundedCornerShape(8.dp))
                )
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