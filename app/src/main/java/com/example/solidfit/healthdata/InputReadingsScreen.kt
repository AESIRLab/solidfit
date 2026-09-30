package com.example.solidfit.healthdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Mass
import com.example.solidfit.R
import com.example.solidfit.ui.theme.WorkoutSolidProjectTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun InputReadingsScreen(
    permissions: Set<String>,
    permissionsGranted: Boolean,
    readingsList: List<WeightRecord>,
    uiState: InputReadingsViewModel.UiState,
    onInsertClick: (Double) -> Unit = {},
    onError: (Throwable?) -> Unit = {},
    onPermissionsResult: () -> Unit = {},
    weeklyAvg: Mass?,
    onPermissionsLaunch: (Set<String>) -> Unit = {},
) {

  // Remember the last error ID, such that it is possible to avoid re-launching the error
  // notification for the same error when the screen is recomposed, or configuration changes etc.
  val errorId = rememberSaveable { mutableStateOf(UUID.randomUUID()) }

  LaunchedEffect(uiState) {
    // If the initial data load has not taken place, attempt to load the data.
    if (uiState is InputReadingsViewModel.UiState.Uninitialized) {
      onPermissionsResult()
    }

    // The [InputReadingsScreenViewModel.UiState] provides details of whether the last action
    // was a success or resulted in an error. Where an error occurred, for example in reading
    // and writing to Health Connect, the user is notified, and where the error is one that can
    // be recovered from, an attempt to do so is made.
    if (uiState is InputReadingsViewModel.UiState.Error && errorId.value != uiState.uuid) {
      onError(uiState.exception)
      errorId.value = uiState.uuid
    }
  }

  var weightInput by remember { mutableStateOf("") }

  // Check if the input value is a valid weight
  fun hasValidDoubleInRange(weight: String): Boolean {
    val tempVal = weight.toDoubleOrNull()
    return if (tempVal == null) {
      false
    } else tempVal <= 1000
  }

  if (uiState != InputReadingsViewModel.UiState.Uninitialized) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (!permissionsGranted) {
        item {
          Button(
            onClick = { onPermissionsLaunch(permissions) }
          ) {
            Text(text = stringResource(R.string.permissions_button_label))
          }
        }
      } else {
        item {
          OutlinedTextField(
            value = weightInput,
            onValueChange = {
              weightInput = it
            },

            label = {
              Text(stringResource(id = R.string.weight_input))
            },
            isError = !hasValidDoubleInRange(weightInput),
            keyboardActions = KeyboardActions { !hasValidDoubleInRange(weightInput) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
          )
          if (!hasValidDoubleInRange(weightInput)) {
            Text(
              text = stringResource(id = R.string.valid_weight_error_message),
              color = Color.Blue,
              modifier = Modifier.padding(start = 16.dp)
            )
          }

          Button(
            enabled = hasValidDoubleInRange(weightInput),
            onClick = {
              onInsertClick(weightInput.toDouble())
              // clear TextField when new weight is entered
              weightInput = ""
            },

            modifier = Modifier.fillMaxHeight()

          ) {
            Text(text = stringResource(id = R.string.add_readings_button))
          }

          Text(
            text = stringResource(id = R.string.previous_readings),
            fontSize = 24.sp,
            color = Color.Blue
          )
        }
        items(readingsList) { reading ->
          Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // show local date and time
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val zonedDateTime =
              dateTimeWithOffsetOrDefault(reading.time, reading.zoneOffset)
            Text(
              text = "${reading.weight}" + " ",
            )
            Text(text = formatter.format(zonedDateTime))
          }
        }
        item {
          Text(
            text = stringResource(id = R.string.weekly_avg), fontSize = 24.sp,
            color = Color.Blue,
            modifier = Modifier.padding(vertical = 20.dp)
          )
          if (weeklyAvg == null) {
            Text(text = "0.0" + stringResource(id = R.string.pounds))
          } else {
            Text(text = "$weeklyAvg".take(5) + stringResource(id = R.string.pounds))
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun InputReadingsScreenPreview() {
  val inputTime = Instant.now()
  WorkoutSolidProjectTheme(darkTheme = false) {
    InputReadingsScreen(
      permissions = setOf(),
      weeklyAvg = Mass.pounds(54.5),
      permissionsGranted = true,
      readingsList = listOf(
        WeightRecord(
          weight = Mass.pounds(54.0),
          time = inputTime,
          zoneOffset = null
        ),
        WeightRecord(
          weight = Mass.pounds(55.0),
          time = inputTime,
          zoneOffset = null
        )
      ),
      uiState = InputReadingsViewModel.UiState.Done
    )
  }
}


