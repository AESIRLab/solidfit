package com.example.solidfit.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.solidfit.R
import com.example.solidfit.WorkoutItemViewModel
import com.example.solidfit.model.WorkoutItem
import com.example.solidfit.session.sessionDetailsFromJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private val PageBg = Color(0xFF0E1110)              // matches your list screen dark background vibe
private val CardBg = Color(0xFFFFFBF5)              // warm paper
private val CardStroke = Color.Black.copy(alpha = 0.10f)

private val TextPrimary = Color(0xFF1C1F24)          // charcoal
private val TextSecondary = Color(0xFF4B5563)        // slate gray
private val IconStrong = Color(0xFF374151)           // icon dark

private val ChipBg = Color(0xFFF0E7DA)               // warm muted surface
private val ChipStroke = Color.Black.copy(alpha = 0.08f)

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun WorkoutCard(
    workout: WorkoutItem,
    viewModel: WorkoutItemViewModel
) {

    val isSession = workout.detailsJson.isNotBlank()
    val sessionDetails = remember(workout.detailsJson) {
        if (!isSession) null
        else runCatching { sessionDetailsFromJson(workout.detailsJson) }.getOrNull()
    }

    val exerciseSummary = remember(workout.detailsJson) {
        val names = sessionDetails?.exercises?.map { it.name }?.filter { it.isNotBlank() }.orEmpty()
        when {
            names.isEmpty() -> ""
            names.size <= 2 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + " +${names.size - 2}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(PageBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ---- HERO IMAGE (if any) ----
            WorkoutHeroImage(workout = workout, viewModel = viewModel)

            // ---- TITLE + DATE HEADER ----
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardBg,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = workout.name.ifBlank { "Workout" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val performedText = if (workout.datePerformed != 0L) {
                        SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                            .format(Date(workout.datePerformed))
                    } else {
                        // fallback to created
                        SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                            .format(Date(workout.dateCreated))
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = performedText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(Modifier.height(12.dp))

                    // ---- STATS CHIPS ----
                    StatsRow(workout = workout)
                }
            }

            // ---- DETAILS ----
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardBg,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = IconStrong
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Details row: Exercise summary
                    if (isSession && exerciseSummary.isNotBlank()) {
                        DetailRow(label = "Exercise", value = exerciseSummary)
                    }


                    if (workout.quantity.isNotBlank()) {
                        DetailRow(label = "Quantity", value = "${workout.quantity} reps")
                    }

                    if (workout.duration.isNotBlank()) {
                        if (workout.duration.toInt() >= 60) {
                            val minDuration = workout.duration.toInt() / 60
                            DetailRow(label = "Duration", value = "$minDuration min")
                        }
                        else {
                            DetailRow(label = "Duration", value = "${workout.duration} sec")
                        }
                    }

                    // Optional (only if you store heart rate)
                    if (workout.heartRate != 0L) {
                        DetailRow(label = "Heart rate", value = "${workout.heartRate} bpm")
                    }

                    val created = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                        .format(Date(workout.dateCreated))
                    DetailRow(label = "Created", value = created)

                    if (workout.dateModified != 0L) {
                        val modified = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                            .format(Date(workout.dateModified))
                        DetailRow(label = "Modified", value = modified)
                    }
                }
            }

            // ---- NOTES ----
            if (workout.notes.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CardBg,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Notes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = workout.notes,
                            fontSize = 16.sp,
                            color = TextSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            if (isSession && sessionDetails != null) {

                // --- Exercises header card (matches Details style) ---
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CardBg,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.exercise_black_24dp),
                            contentDescription = null,
                            tint = IconStrong,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Exercises",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${sessionDetails.exercises.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }

//                Spacer(Modifier.height(10.dp))

                sessionDetails.exercises.forEach { ex ->
                    ExerciseBreakdownCard(exerciseName = ex.name, sets = ex.sets)
//                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkoutHeroImage(
    workout: WorkoutItem,
    viewModel: WorkoutItemViewModel
) {
    if (workout.mediaUri.isBlank()) return

    val ctx = LocalContext.current
    val model = remember(workout.mediaUri, workout.dateModified) {
        val s = workout.mediaUri
        when {
            s.isBlank() -> null
            s.startsWith("content", true) -> Uri.parse(s)
            else -> viewModel.buildAuthorizedImageRequest(ctx, s) ?: s
        }
    }

    if (model == null) return

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .border(1.dp, CardStroke, RoundedCornerShape(20.dp))
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = "Workout photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(26.dp)) }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.06f)),
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

@Composable
private fun StatsRow(workout: WorkoutItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (workout.duration.isNotBlank()) {
            if (workout.duration.toInt() >= 60) {
                val minDuration = workout.duration.toInt() / 60
                StatChip(
                    icon = painterResource(R.drawable.schedule_24px),
                    label = " $minDuration min"
                )
            }
            else {
                StatChip(
                    icon = painterResource(R.drawable.schedule_24px),
                    label = " ${workout.duration} sec"
                )
            }
        }
        if (workout.quantity.isNotBlank()) {
            StatChip(
                icon = painterResource(R.drawable.laps_24px),
                label = "${workout.quantity} reps"
            )
        }
        if (workout.workoutType.isNotBlank()) {
            StatChip(
                icon = painterResource(R.drawable.assignment_24px),
                label = workout.workoutType
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: Painter,
    label: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ChipBg),
        shape = RoundedCornerShape(999.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.border(1.dp, ChipStroke, RoundedCornerShape(999.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = IconStrong,          // works for vector XML
                modifier = Modifier.size(18.dp)
            )
//            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            color = TextPrimary
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ExerciseBreakdownCard(
    exerciseName: String,
    sets: List<com.example.solidfit.session.SetEntry>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardStroke, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exerciseName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.weight(1f))

                // small pill: "N sets"
                Card(
                    colors = CardDefaults.cardColors(containerColor = ChipBg),
                    shape = RoundedCornerShape(999.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.border(1.dp, ChipStroke, RoundedCornerShape(999.dp))
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        text = "${sets.size} sets",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            sets.forEachIndexed { idx, set ->
                SetRow(
                    setNumber = idx + 1,
                    reps = set.reps,
                    weight = set.weight
                )

                if (idx != sets.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.Black.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SetRow(setNumber: Int, reps: Int, weight: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Set 1" chip
        Card(
            colors = CardDefaults.cardColors(containerColor = ChipBg),
            shape = RoundedCornerShape(999.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.border(1.dp, ChipStroke, RoundedCornerShape(999.dp))
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                text = "Set $setNumber",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = "$reps reps",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )

        Spacer(Modifier.weight(1f))

        if (weight != null) {
            Text(
                text = formatWeight(weight),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

private fun formatWeight(weight: Double): String {
    val asInt = weight.toInt()
    val pretty = if (weight == asInt.toDouble()) asInt.toString() else weight.toString()
    return "$pretty lb"
}
