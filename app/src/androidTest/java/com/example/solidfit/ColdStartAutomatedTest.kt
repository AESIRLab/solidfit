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
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        device.pressHome()

        // Use the instrumentation targetContext for a more stable launch
        val pkgName = "com.example.solidfit"
        val context = instrumentation.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(pkgName)

        if (intent == null) {
            throw RuntimeException("Could not find launcher intent for $pkgName. Is the app installed?")
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait up to 10 seconds for the app to actually appear
        device.wait(Until.hasObject(By.pkg(pkgName).depth(0)), 10000)

        // --- STABILIZED CLICKS ---
        // Use a shorter wait for the buttons so we don't hang if they don't appear
        val allowRegex = Pattern.compile("(?i)allow|while using the app|allow all")
        device.wait(Until.findObject(By.text(allowRegex)), 2000)?.click()

        val loginRegex = Pattern.compile("(?i).*log in.*")
        device.wait(Until.findObject(By.text(loginRegex)), 5000)?.click()

        // Wait for the fetch trace
        Thread.sleep(5000)

        device.pressHome()

        Thread.sleep(5000)
    }
}


//adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
//adb install -r -t -g app/build/outputs/apk/debug/app-debug.apk
//adb shell pm list packages | findstr "solidfit"
//

//for ($i=1; $i -le 28; $i++) {
//    Write-Host "Run ${i}: Testing Cold Start..."
//    adb shell am force-stop com.example.solidfit
//
//    # Run the test
//    adb shell am instrument -w -e class com.example.solidfit.ColdStartAutomatedTest com.example.solidfit.test/androidx.test.runner.AndroidJUnitRunner
//
//    # 3. Increase the total gap between runs to 30 seconds
//    # This ensures the OS and the Firebase SDK have completely reset.
//    Write-Host "Run ${i} complete. Waiting for cloud sync..."
//    Start-Sleep -Seconds 10
//}