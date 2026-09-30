package com.example.solidfit

import android.content.Intent
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

        val pkgName = "com.example.solidfit"
        val context = instrumentation.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(pkgName)
            ?: throw RuntimeException("Could not find launcher intent for $pkgName. Is the app installed?")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait up to 10 seconds for the app to actually appear
        device.wait(Until.hasObject(By.pkg(pkgName).depth(0)), 10000)

        // --- STREAMLINED CLICKS ---
        // Since permissions are handled by ADB, we skip straight to the main UI.
        // This will find and click "1" the exact millisecond it renders on screen.
        device.wait(Until.findObject(By.text("1")), 5000)?.click()

        // Wait for the Firebase trace to finish logging
        Thread.sleep(2000)

        device.pressHome()
    }
}
//$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
//.\gradlew.bat assembleDebug assembleDebugAndroidTest
//adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
//adb install -r -t -g app/build/outputs/apk/debug/app-debug.apk
//
//for ($i=1; $i -le 30; $i++) {
//    Write-Host "`n--- Run ${i}: Testing Cold Start ---"
//
//    adb shell pm clear com.example.solidfit
//
//    # Re-grant ALL permissions immediately after clearing
//            adb shell pm grant com.example.solidfit android.permission.POST_NOTIFICATIONS 2>$null
//    adb shell pm grant com.example.solidfit android.permission.READ_MEDIA_IMAGES 2>$null
//    adb shell pm grant com.example.solidfit android.permission.READ_MEDIA_VIDEO 2>$null
//    adb shell pm grant com.example.solidfit android.permission.READ_MEDIA_VISUAL_USER_SELECTED 2>$null
//
//    # --- THE FIX: Bluetooth / Nearby Devices Permissions ---
//    adb shell pm grant com.example.solidfit android.permission.BLUETOOTH_SCAN 2>$null
//    adb shell pm grant com.example.solidfit android.permission.BLUETOOTH_CONNECT 2>$null
//    # -------------------------------------------------------
//
//    # Run the test
//    adb shell am instrument -w -e class com.example.solidfit.ColdStartAutomatedTest com.example.solidfit.test/androidx.test.runner.AndroidJUnitRunner
//
//    # Wait 15s for Firebase to save trace
//    Write-Host "Run ${i} complete. Waiting 15s for Firebase to save trace..."
//    Start-Sleep -Seconds 3
//}
