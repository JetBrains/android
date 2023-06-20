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

    @get:Rule var rule = ActivityScenarioRule(MyActivity::class.java)

    @Test
    fun viewIsUpdatedBySdk() {
        InstrumentationRegistry.getInstrumentation().apply {
            waitForIdle {
                val bitmap = uiAutomation.takeScreenshot()
                assert(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == Color.RED)
            }
        }
    }
}
