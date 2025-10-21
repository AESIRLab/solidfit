package com.example.solidfit.notifications

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {

        // Skips notification if a workout is logged
        if (isWorkoutLoggedToday(applicationContext)) {
            return Result.success()
        }

        showNotification(
            applicationContext,
            "Workout Reminder",
            "Don't forget to log your workout today!",
            "workout_reminder_channel"
        )

        // Schedule the next notification check after 24 hours if needed
        scheduleNextNotification()

        return Result.success()
    }

    private fun scheduleNextNotification() {
        val nextRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(nextRequest)
    }

    private fun isWorkoutLoggedToday(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("workoutPrefs", Context.MODE_PRIVATE)
        val lastWorkoutDate = sharedPreferences.getString("lastWorkoutDate", null)
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastWorkoutDate == todayDate
    }
}
