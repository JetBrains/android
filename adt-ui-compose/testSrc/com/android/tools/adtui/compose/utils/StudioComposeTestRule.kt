/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.compose.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.android.tools.adtui.compose.StudioTestTheme
import java.io.File

abstract class StudioComposeTestRule(val composeTestRule: ComposeContentTestRule = createComposeRule())
  : ComposeContentTestRule by composeTestRule {
  companion object {
    private const val SKIKO_PATH_PROPERTY_KEY = "skiko.data.path"

    private fun setupSkikoBinaryExtractionFolder() {
      val skikoPathPropertyValue = File(System.getProperty("java.io.tmpdir")).resolve(".skiko").toString()
      System.setProperty(SKIKO_PATH_PROPERTY_KEY, skikoPathPropertyValue)
    }

    fun createStudioComposeTestRule(): StudioComposeTestRule {
      // Setup binary extraction folder necessary for bazel test execution.
      setupSkikoBinaryExtractionFolder()
      return StudioComposeTestRuleImpl()
    }
  }

  abstract fun setContent(darkMode: Boolean, composable: @Composable () -> Unit)
}

/**
 * Functionally equivalent to the test rule created by `createComposeRule`, but overrides the behavior of setContent to include a theme.
 */
private class StudioComposeTestRuleImpl : StudioComposeTestRule() {
  override fun setContent(darkMode: Boolean, composable: @Composable () -> Unit) {
    super.setContent {
      StudioTestTheme(darkMode) {
        composable()
      }
    }
  }

  override fun setContent(composable: @Composable () -> Unit) {
    setContent(false, composable)
  }
}