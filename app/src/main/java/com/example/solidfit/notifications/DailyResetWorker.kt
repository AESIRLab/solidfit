package com.example.solidfit.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyResetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // Resets the notification flag in SharedPreferences
        val sharedPreferences = applicationContext.getSharedPreferences("workoutPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("lastNotificationDate")
        editor.apply()

        return Result.success()
    }
}
