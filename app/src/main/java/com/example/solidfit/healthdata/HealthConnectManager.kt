package com.example.solidfit.healthdata

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.random.Random

// The minimum android level that can use Health Connect
const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O_MR1

/**
 * Demonstrates reading and writing from Health Connect.
 */
class HealthConnectManager(private val context: Context) {

  private var client: HealthConnectClient? = null

  var availability = mutableStateOf(HealthConnectAvailability.NOT_SUPPORTED)
    private set

//  init {
//    checkAvailability()
//  }

  fun checkAvailability() {
    availability.value = when (HealthConnectClient.getSdkStatus(context)) {
      SDK_AVAILABLE -> {
        client = HealthConnectClient.getOrCreate(context)
        HealthConnectAvailability.INSTALLED
      }
      else ->
        if (Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK) {
          client = null
          HealthConnectAvailability.NOT_INSTALLED
        }
        else {
          client = null
          HealthConnectAvailability.NOT_SUPPORTED
        }
    }
  }

  fun isReady(): Boolean = client != null

  private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

  /**
   * Determines whether all the specified permissions are already granted. It is recommended to
   * call [PermissionController.getGrantedPermissions] first in the permissions flow, as if the
   * permissions are already granted then there is no need to request permissions via
   * [PermissionController.createRequestPermissionResultContract].
   */
  suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
    val c = client ?: return false
    val granted = c.permissionController.getGrantedPermissions()
    return granted.containsAll(permissions)
  }

  fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
    return PermissionController.createRequestPermissionResultContract()
  }

  private fun showInstallToast() {
    Toast.makeText(
      context,
      "Health Connect isn't installed. Please install it from the Play Store.",
      Toast.LENGTH_LONG
    ).show()
  }

  // Writes WeightRecord to Health Connect.
  suspend fun writeWeightInput(weightInput: Double) {
    val c = client ?: return showInstallToast()
    val time = ZonedDateTime.now().withNano(0)
    val weightRecord = WeightRecord(
        weight = Mass.pounds(weightInput),
        time = time.toInstant(),
        zoneOffset = time.offset
    )
    val records = listOf(weightRecord)
    try {
      healthConnectClient.insertRecords(records)
      Toast.makeText(context, "Successfully inserted records", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Toast.makeText(context, e.message.toString(), Toast.LENGTH_SHORT).show()
    }
  }

  // Reads in existing WeightRecords
  suspend fun readWeightInputs(start: Instant, end: Instant): List<WeightRecord> {
    val c = client ?: return emptyList()
    val request = ReadRecordsRequest(
      recordType = WeightRecord::class,
      timeRangeFilter = TimeRangeFilter.between(start, end)
    )
    val response = healthConnectClient.readRecords(request)
    return response.records
  }

  // Returns the weekly average of WeightRecords.
  suspend fun computeWeeklyAverage(start: Instant, end: Instant): Mass? {
    val request = AggregateRequest(
      metrics = setOf(WeightRecord.WEIGHT_AVG),
      timeRangeFilter = TimeRangeFilter.between(start, end)
    )
    val response = healthConnectClient.aggregate(request)
    return response[WeightRecord.WEIGHT_AVG]
  }

  suspend fun writeHeartRateInput(bpm: Double) {
    val nowZdt = ZonedDateTime.now().withNano(0)
    val sample = HeartRateRecord.Sample(
      time = nowZdt.toInstant(),
      beatsPerMinute = bpm.toLong()
    )
    val record = HeartRateRecord(
      startTime         = nowZdt.toInstant(),
      startZoneOffset   = nowZdt.offset,
      endTime           = nowZdt.toInstant(),
      endZoneOffset     = nowZdt.offset,
      samples           = listOf(sample)
    )
    try {
      healthConnectClient.insertRecords(listOf(record))
//      Toast.makeText(context, "Inserted heart rate: ${bpm.toInt()} BPM", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Toast.makeText(context, "Error inserting HR: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  /** Reads all HeartRateRecord samples between start and end. */
  suspend fun readHeartRateInputs(start: Instant, end: Instant): List<HeartRateRecord> {
    val req = ReadRecordsRequest(
      recordType      = HeartRateRecord::class,
      timeRangeFilter = TimeRangeFilter.between(start, end)
    )
    return healthConnectClient.readRecords(req).records
  }

  // Builds HeartRateRecord
  private fun buildHeartRateSeries(
    sessionStartTime: ZonedDateTime,
    sessionEndTime: ZonedDateTime,
  ): HeartRateRecord {
    val samples = mutableListOf<HeartRateRecord.Sample>()
    var time = sessionStartTime
    while (time.isBefore(sessionEndTime)) {
      samples.add(
        HeartRateRecord.Sample(
          time = time.toInstant(),
          beatsPerMinute = (80 + Random.nextInt(80)).toLong()
        )
      )
      time = time.plusSeconds(30)
    }
    return HeartRateRecord(
      startTime = sessionStartTime.toInstant(),
      startZoneOffset = sessionStartTime.offset,
      endTime = sessionEndTime.toInstant(),
      endZoneOffset = sessionEndTime.offset,
      samples = samples
    )
  }

  /**
   * TODO: Obtains a changes token for the specified record types.
   */
  suspend fun getChangesToken(): String {
    return healthConnectClient.getChangesToken(
      ChangesTokenRequest(
        setOf(
          ExerciseSessionRecord::class
        )
      )
    )
  }

  /**
   * TODO: Retrieve changes from a changes token.
   */
  suspend fun getChanges(token: String): Flow<ChangesMessage> = flow {
    var nextChangesToken = token
    do {
      val response = healthConnectClient.getChanges(nextChangesToken)
      if(response.changesTokenExpired) {
        throw IOException("Changes token has expired")
      }
      emit(ChangesMessage.ChangeList(response.changes))
      nextChangesToken = response.nextChangesToken
    } while (response.hasMore)
    emit(ChangesMessage.NoMoreChanges(nextChangesToken))
  }


  /**
   * Convenience function to reuse code for reading data.
   */
  private suspend inline fun <reified T : Record> readData(
      timeRangeFilter: TimeRangeFilter,
      dataOriginFilter: Set<DataOrigin> = setOf(),
  ): List<T> {
    val request = ReadRecordsRequest(
      recordType = T::class,
      dataOriginFilter = dataOriginFilter,
      timeRangeFilter = timeRangeFilter
    )
    return healthConnectClient.readRecords(request).records
  }

  private fun isSupported() = Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK

  // Represents the two types of messages that can be sent in a Changes flow.
  sealed class ChangesMessage {
    data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()
    data class ChangeList(val changes: List<Change>) : ChangesMessage()
  }
}

/**
 * Health Connect requires that the underlying Health Connect APK is installed on the device.
 * [HealthConnectAvailability] represents whether this APK is indeed installed, whether it is not
 * installed but supported on the device, or whether the device is not supported (based on Android
 * version).
 */
enum class HealthConnectAvailability {
  INSTALLED,
  NOT_INSTALLED,
  NOT_SUPPORTED
}
