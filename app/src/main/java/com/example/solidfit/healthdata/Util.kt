package com.example.solidfit.healthdata

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
//import com.example.healthconnect.codelab.data.dateTimeWithOffsetOrDefault
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows details of a given throwable in the snackbar
 */
fun showExceptionSnackbar(
  snackbarHostState: SnackbarHostState,
  scope: CoroutineScope,
  throwable: Throwable?,
) {
  scope.launch {
    snackbarHostState.showSnackbar(
      message = throwable?.localizedMessage ?: "Unknown exception",
      duration = SnackbarDuration.Short
    )
  }
}

fun formatDisplayTimeStartEnd(
  startTime: Instant,
  startZoneOffset: ZoneOffset?,
  endTime: Instant,
  endZoneOffset: ZoneOffset?,
): String {
  val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
  val start = timeFormatter.format(dateTimeWithOffsetOrDefault(startTime, startZoneOffset))
  val end = timeFormatter.format(dateTimeWithOffsetOrDefault(endTime, endZoneOffset))
  return "$start - $end"
}
