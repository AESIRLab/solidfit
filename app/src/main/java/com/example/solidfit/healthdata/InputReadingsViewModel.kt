package com.example.solidfit.healthdata

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.RemoteException
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Mass
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class InputReadingsViewModel(
  private val healthConnectManager: HealthConnectManager,
  appContext: Context
) : ViewModel() {
  val permissions = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getWritePermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getWritePermission(WeightRecord::class)
  )
  var weightWeeklyAvg: MutableState<Mass?> = mutableStateOf(Mass.pounds(0.0))
    private set

//  var heartWeeklyAvg: MutableState<Double?> = mutableStateOf(0.0)
//    private set

  var permissionsGranted = mutableStateOf(false)
    private set

  var weightReadingsList: MutableState<List<WeightRecord>> = mutableStateOf(listOf())
    private set

  var heartReadingsList: MutableState<List<HeartRateRecord>> = mutableStateOf(listOf())
    private set

  var uiState: UiState by mutableStateOf(UiState.Uninitialized)
    private set

  val permissionsLauncher = healthConnectManager.requestPermissionsActivityContract()

  private val _devices = mutableStateListOf<BluetoothDevice>()
  val devices: List<BluetoothDevice> = _devices

  private val _currentBpm = mutableStateOf<Int?>(null)
  val currentBpm: Int? get() = _currentBpm.value

  private var selectedDevice: BluetoothDevice? = null

  private val bleManager = HeartRateBleManager(
    appContext,
    onDeviceFound = { dev ->
      if (dev !in _devices) _devices += dev
    },
    onBpm = { bpm ->
      _currentBpm.value = bpm
      inputHeartRate(bpm.toDouble())
    },
    onDisconnect = {
      _currentBpm.value = null
      _devices.clear()
      startBleScan()
    }
  )

  fun startBleScan() {
    _devices.clear()
    _currentBpm.value = null
    selectedDevice = null
    bleManager.startScan()
  }

  fun connectTo(device: BluetoothDevice) {
    selectedDevice = device
    bleManager.connectTo(device)
  }

  private fun resumeConnection() {
    selectedDevice?.let {
      bleManager.connectTo(it)
    }
  }

  override fun onCleared() {
    super.onCleared()
    bleManager.stop()
  }

  fun initialLoad() {
    viewModelScope.launch {
      tryWithPermissionsCheck {
        readWeightInputs()
        readHeartRateInputs()
      }
    }
  }

  fun inputReadings(inputValue: Double) {
    viewModelScope.launch {
      tryWithPermissionsCheck {
        healthConnectManager.writeWeightInput(inputValue)
        readWeightInputs()
      }
    }
  }

  fun inputHeartRate(bpm: Double) {
    viewModelScope.launch {
      tryWithPermissionsCheck {
        healthConnectManager.writeHeartRateInput(bpm)
        readHeartRateInputs()
      }
    }
  }

  private suspend fun readHeartRateInputs() {
    val startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()
    val now = Instant.now()
    heartReadingsList.value = healthConnectManager.readHeartRateInputs(startOfDay,now)
  }

  private suspend fun readWeightInputs() {
    val startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)
    val now = Instant.now()
    val endofWeek = startOfDay.toInstant().plus(7, ChronoUnit.DAYS)
    weightReadingsList.value = healthConnectManager.readWeightInputs(startOfDay.toInstant(), now)
    weightWeeklyAvg.value =
      healthConnectManager.computeWeeklyAverage(startOfDay.toInstant(), endofWeek)
  }

  /**
   * Provides permission check and error handling for Health Connect suspend function calls.
   *
   * Permissions are checked prior to execution of [block], and if all permissions aren't granted
   * the [block] won't be executed, and [permissionsGranted] will be set to false, which will
   * result in the UI showing the permissions button.
   *
   * Where an error is caught, of the type Health Connect is known to throw, [uiState] is set to
   * [UiState.Error], which results in the snackbar being used to show the error message.
   */
  private suspend fun tryWithPermissionsCheck(block: suspend () -> Unit) {
    permissionsGranted.value = healthConnectManager.hasAllPermissions(permissions)
    uiState = try {
      if (permissionsGranted.value) {
        block()
      }
      UiState.Done
    } catch (remoteException: RemoteException) {
      UiState.Error(remoteException)
    } catch (securityException: SecurityException) {
      UiState.Error(securityException)
    } catch (ioException: IOException) {
      UiState.Error(ioException)
    } catch (illegalStateException: IllegalStateException) {
      UiState.Error(illegalStateException)
    }
  }

  sealed class UiState {
    object Uninitialized : UiState()
    object Done : UiState()

    // A random UUID is used in each Error object to allow errors to be uniquely identified, and recomposition won't result in multiple snackbars.
    data class Error(val exception: Throwable, val uuid: UUID = UUID.randomUUID()) : UiState()
  }


}

class InputReadingsViewModelFactory(
    private val healthConnectManager: HealthConnectManager,
    private val appContext: Context
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(InputReadingsViewModel::class.java)) {
      @Suppress("UNCHECKED_CAST")
      return InputReadingsViewModel(
        healthConnectManager = healthConnectManager,
        appContext = appContext.applicationContext
        ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}
