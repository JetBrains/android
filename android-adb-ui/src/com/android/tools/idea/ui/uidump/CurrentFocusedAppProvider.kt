/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.ui.uidump

import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.project.Project

internal const val FOCUSED_APP_COMMAND = "dumpsys window"

class CurrentFocusedAppProvider {
  suspend fun focusedApp(project: Project, serialNumber: String): String {
    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val adbLibService = AdbLibService.getInstance(project)

    val shellOutput =
      adbLibService.session.deviceServices.shellAsText(deviceSelector, FOCUSED_APP_COMMAND)

    if (shellOutput.exitCode != 0) {
      return "$FOCUSED_APP_COMMAND failed with exit code ${shellOutput.exitCode}. ${shellOutput.stderr}"
    }
    return shellOutput.stdout
      .lines()
      .filter { it.contains("mCurrentFocus") || it.contains("mFocusedApp") }
      .joinToString("\n")
  }
}
