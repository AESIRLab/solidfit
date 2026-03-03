package com.example.solidfit

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class ColdStartAutomatedTest {

    @Test
    fun executeColdStartAndLogin() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // 1. Start from the home screen
        device.pressHome()

        // 2. Launch the app manually
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage("com.example.workoutroomproject")
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for the app to open
        device.wait(Until.hasObject(By.pkg("com.example.workoutroomproject").depth(0)), 5000)

        // 3. You are already logged in! Just wait for the 60KB fetch trace to complete.
        Thread.sleep(15000)

        // 4. Press the Home button to force the Firebase trace upload
        device.pressHome()

        // Give Firebase a few seconds to finish the background upload
        Thread.sleep(4000)
    }
}


//for ($i=1; $i -le 30; $i++) {
//    Write-Host "Run ${i}: Force Stopping App..."
//    adb shell am force-stop com.example.workoutroomproject
//
//            Write-Host "Run ${i}: Starting test..."
//    adb shell am instrument -w -e class com.example.solidfit.ColdStartAutomatedTest com.example.workoutroomproject.test/androidx.test.runner.AndroidJUnitRunner
//
//    Start-Sleep -Seconds 4
//}