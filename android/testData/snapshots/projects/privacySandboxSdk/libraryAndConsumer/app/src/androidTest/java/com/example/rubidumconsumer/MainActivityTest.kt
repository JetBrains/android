package com.example.rubidumconsumer

import android.graphics.Color
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule var rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun viewIsUpdatedBySdk() {
        val numRetries = 30
        val sleepDuration = 1000L
        repeat(numRetries) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap == null) {
                return@repeat // Continue looping in case the screenshot fails
            }
            if (bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == Color.RED) {
                return@viewIsUpdatedBySdk
            }
            Thread.sleep(sleepDuration)
        }
        throw Exception("View not updated after $numRetries attempts.")
    }
}
