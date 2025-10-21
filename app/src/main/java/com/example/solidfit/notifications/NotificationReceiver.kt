package com.example.solidfit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// TODO: NOTIFICATIONS ARE BROKEN D:

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("WORKOUT NOTIFICATIONS", "Device booted, scheduling notification workers.")

            // Schedules the daily notification and reset functions
            Log.d("WORKOUT NOTIFICATIONS", "Calling scheduleDailyNotification")
            scheduleDailyNotification(context)
            scheduleDailyReset(context)
        }
    }

    private fun scheduleDailyNotification(context: Context) {
        val dailyWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DailyWorkoutNotification",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )
        Log.d("WORKOUT NOTIFICATIONS", "Scheduled daily notification worker")
    }

    private fun calculateInitialDelay(): Long {
        Log.d("WORKOUT NOTIFICATIONS", "enter calculateInitialDelay()")
        // TODO: After testing, ensure hour is back to 15; min & sec = 0
        // Every day at 3:00pm
        val targetHour = 12
        val now = java.util.Calendar.getInstance()
        val targetTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, 25)
            set(java.util.Calendar.SECOND, 30)
            if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return targetTime.timeInMillis - now.timeInMillis
    }

    private fun scheduleDailyReset(context: Context) {
        // TODO: Ensure the this function actually resets the notification flag daily
        // Schedules the reset worker to run every 24 hours
        val dailyResetRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calculateMidnightDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DailyResetWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyResetRequest
        )
    }

    // Calculates how long until midnight
    private fun calculateMidnightDelay(): Long {
        val now = java.util.Calendar.getInstance()
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return midnight.timeInMillis - now.timeInMillis
    }
}
