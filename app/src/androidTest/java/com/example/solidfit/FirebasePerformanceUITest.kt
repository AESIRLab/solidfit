package com.example.solidfit

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.rule.GrantPermissionRule
import android.Manifest

@RunWith(AndroidJUnit4::class)
class FirebasePerformanceUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun generateTracesForInsertAndDelete() {
        // Let the app settle and load data first
        composeTestRule.waitForIdle()
        Thread.sleep(15000)

        // --- 1. TRIGGER THE INSERT TRACE 30 TIMES ---
        repeat(30) { index ->

            composeTestRule.onNodeWithContentDescription("Add workout").performClick()
            composeTestRule.waitForIdle()

            // Added the $index variable so each workout has a unique name (e.g., "Test Cardio 1", "Test Cardio 2")
            composeTestRule.onNodeWithText("Title (optional)").performTextInput("Test Cardio $index")
            composeTestRule.onNodeWithText("Notes (optional)").performTextInput("Test Note")

            composeTestRule.onNodeWithText("New exercise name").performTextInput("testexercise")
            composeTestRule.onNodeWithText("Add").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Reps").performTextInput("15")
            composeTestRule.onNodeWithText("Add Set").performClick()

            // Wait for the set to be added so the Save button becomes enabled
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Stop & Save").performClick()

            // Wait for the navigation back to the WorkoutList to finish before starting the next loop
            composeTestRule.waitForIdle()
            Thread.sleep(500) // Brief pause to let the list UI settle
        }

        // --- 3. THE FIREBASE UPLOAD WINDOW ---
        // Pausing for 10 seconds to guarantee the background upload completes.
        Thread.sleep(20000)
    }

    @Test
    fun generateTracesForFetchAll() {
        composeTestRule.waitForIdle()
        Thread.sleep(10000)

        // Loop the fetch 30 times
        repeat(30) {
            composeTestRule.onNodeWithText("Fetch All (RTDB)").performClick()

            // Wait 3 seconds to ensure the 120KB payload fully downloads before clicking again
            composeTestRule.waitForIdle()
            Thread.sleep(3000)
        }

        Thread.sleep(10000)
    }
}

