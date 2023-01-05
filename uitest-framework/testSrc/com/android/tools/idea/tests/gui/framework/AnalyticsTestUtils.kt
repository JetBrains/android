/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework

import com.android.tools.idea.stats.ConsentDialog
import com.android.tools.idea.tests.gui.framework.fixture.ConsentDialogFixture.Companion.find
import com.intellij.openapi.application.PathManager
import kotlin.io.path.exists
import org.fest.swing.core.Robot
import org.fest.swing.exception.WaitTimedOutError
import java.lang.Boolean.getBoolean
import java.nio.file.Files
import java.nio.file.Paths

private const val ENABLE_LOGGING_PROPERTY = "enable.android.analytics.logging.for.test"

private val consentFile
  get() = PathManager.getCommonDataPath().resolve("consentOptions/accepted")

private val analyticsSettingsFile
  get() = Paths.get(System.getProperty("user.home")).resolve(".android/analytics.settings")

private val enableLogging
  get() = getBoolean(ENABLE_LOGGING_PROPERTY)

fun deleteConsentFile() {
  Files.deleteIfExists(consentFile)
}

fun deleteAnalyticsFile() {
  Files.deleteIfExists(analyticsSettingsFile)
}

fun setupConsentFile() {
  deleteConsentFile()

  if (ConsentDialog.isConsentDialogEnabledInTests) {
    return
  }

  val directory = consentFile.parent
  Files.createDirectories(directory)

  val enabled = if (enableLogging) 1 else 0
  consentFile.toFile().writeText("rsch.send.usage.stat:1.0:${enabled}:${System.currentTimeMillis()}")
}

fun setupAnalyticsFile() {
  deleteAnalyticsFile()

  val directory = analyticsSettingsFile.parent
  Files.createDirectories(directory)

  // To suppress the consent dialog when logging is disabled, write an analytics settings file
  // with a lastOptinPromptVersion far in the future
  if (!ConsentDialog.isConsentDialogEnabledInTests && !enableLogging) {
    analyticsSettingsFile.toFile().writeText(
      "{\"userId\":\"e7048a7a-7bae-4093-85dc-c4c1f99190cd\",\"saltSkew\":-1,\"lastOptinPromptVersion\":\"9999.9999\"}")
  }
}

fun consentFileExists(): Boolean {
  return consentFile.exists()
}

class AnalyticsTestUtils {
  companion object {
    @JvmStatic
    val vmDialogOption
      get() = "-D${ConsentDialog.ENABLE_DIALOG_PROPERTY}=${ConsentDialog.isConsentDialogEnabledInTests}"

    val vmLoggingOption
      get() = "-D$ENABLE_LOGGING_PROPERTY=$enableLogging"

    @JvmStatic
    fun dismissConsentDialogIfShowing(robot: Robot) {
      if (!ConsentDialog.isConsentDialogEnabledInTests) {
        return
      }
      val fixture = try {
        find(robot)
      }
      catch (e: WaitTimedOutError) {
        return
      }

      if (enableLogging) {
        fixture.optIn()
      }
      else {
        fixture.decline()
      }
    }
  }
}