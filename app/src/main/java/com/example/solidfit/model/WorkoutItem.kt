package com.example.solidfit.model

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.solidfit.R
import com.example.solidfit.WorkoutItemViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.solidfit.session.sessionDetailsFromJson


//@SolidDefaultTokenStore
//@SolidDefaultUtilities
//@SolidDaoAnnotation
//@SolidDaoImplAnnotation(
//    "http://www.w3.org/2024/ci/core#",
//    "AndroidApplication/SolidFit"
//)
//@SolidDbAnnotation
//@SolidRemoteDataSource
//@SolidAnnotation(
//    "http://www.w3.org/2024/ci/core#",
//    "AndroidApplication/SolidFit"
//)

data class WorkoutItem(
    var id: String,
    var name: String = "",
    var dateCreated: Long = System.currentTimeMillis(),
    var dateModified: Long,
    var quantity: String,
    var duration: String,
    var heartRate: Long,
    var workoutType: String = "",
    var datePerformed: Long = System.currentTimeMillis(),
    var notes: String = "",
    var mediaUri: String = "",
    var detailsJson: String = ""
)

val CardTextPrimary = Color(0xFF1C1F24)
val CardTextSecondary = Color(0xFF4B5563)
val CardIconColor = Color(0xFF374151)

@Composable
fun WorkoutItem(
    workout: WorkoutItem,
    viewModel: WorkoutItemViewModel,
    onDelete: (WorkoutItem) -> Unit,
    onEdit: (WorkoutItem) -> Unit,
    onSelect: (WorkoutItem) -> Unit
) {

    val isSession = workout.detailsJson.isNotBlank()

    val sessionSummary = remember(workout.detailsJson) {
        if (!isSession) null
        else runCatching { sessionDetailsFromJson(workout.detailsJson) }.getOrNull()
    }

    val exerciseCount = sessionSummary?.exercises?.size ?: 0
    val setCount = sessionSummary?.exercises?.sumOf { it.sets.size } ?: 0

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFBF5),            // warm paper
            contentColor = CardTextPrimary,
            disabledContainerColor = Color(0xFFF0E7DA),
            disabledContentColor = CardTextPrimary.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onSelect(workout) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        // NAME
        Text(
            text = workout.name,
            color = CardTextPrimary,
            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            maxLines = 2,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column (
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp)
            ){

                // DATE PERFORMED
                if(workout.datePerformed != 0L) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                append(
                                    SimpleDateFormat("EEEE, MMM. d", Locale.getDefault()).format(
                                    Date(workout.datePerformed)
                                    )
                                )
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    if (workout.duration.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.schedule_24px),
                                contentDescription = "Duration icon",
                                modifier = Modifier.padding(end = 5.dp)
                            )

                            if (workout.duration.toInt() >= 60) {
                                val minDuration = workout.duration.toInt() / 60
                                Text(
                                    modifier = Modifier.padding(end = 12.dp),
                                    text = buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                            append(minDuration.toString())
                                        }
                                        append(" min")
                                    }
                                )
                            }
                            else {
                                Text(
                                    modifier = Modifier.padding(end = 12.dp),
                                    text = buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) {
                                            append(workout.duration)
                                        }
                                        append(" sec")
                                    }
                                )
                            }
                        }
                    }

                    // Session summary (only if detailsJson exists)
                    if (isSession && sessionSummary != null) {
                        Text(text = "Exercises: $exerciseCount")
                        Text(text = "Sets: $setCount")

                        if (workout.heartRate > 0) {
                            Text(text = "Avg HR: ${workout.heartRate}")
                        }
                    } else {
                        // Legacy fields
                        if (workout.quantity.isNotEmpty()) { /* existing quantity UI */ }
                        if (workout.workoutType.isNotEmpty()) { /* existing workoutType UI */ }
                    }

                }
    }

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.padding(top = 4.dp)) {

                    // EDIT BUTTON
                    IconButton(onClick = { onEdit(workout) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit workout",
                            tint = CardIconColor
                        )
                    }

                    // DELETE BUTTON
                    IconButton(onClick = { onDelete(workout) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete workout",
                            tint = CardIconColor
                        )
                    }
                }
            }
        }
    }
}
