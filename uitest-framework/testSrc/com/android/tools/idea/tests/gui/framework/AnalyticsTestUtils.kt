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

import com.android.tools.idea.tests.gui.framework.fixture.ConsentDialogFixture.Companion.find
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.exists
import org.fest.swing.core.Robot
import org.fest.swing.exception.WaitTimedOutError
import java.lang.Boolean.getBoolean
import java.nio.file.Files

private const val ENABLE_DIALOG_PROPERTY = "enable.android.analytics.consent.dialog.for.test"

private val consentFile
  get() = PathManager.getCommonDataPath().resolve("consentOptions/accepted")

private val enableDialog
  get() = getBoolean(ENABLE_DIALOG_PROPERTY)

fun deleteConsentFile() {
  Files.deleteIfExists(consentFile)
}

fun setupConsentFile() {
  deleteConsentFile()

  if (enableDialog) {
    return
  }

  val directory = consentFile.parent
  Files.createDirectories(directory)
  consentFile.toFile().writeText("rsch.send.usage.stat:1.0:1:${System.currentTimeMillis()}")
}

fun consentFileExists(): Boolean {
  return consentFile.exists()
}

class AnalyticsTestUtils {
  companion object {
    @JvmStatic
    val vmOption
      get() = "-D$ENABLE_DIALOG_PROPERTY=$enableDialog"

    @JvmStatic
    fun dismissConsentDialogIfShowing(robot: Robot) {
      if (!enableDialog) {
        return
      }
      val fixture = try {
        find(robot)
      }
      catch (e: WaitTimedOutError) {
        return
      }

      fixture.optIn()
    }
  }
}