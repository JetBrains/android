/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * Mostly just return `adb shell uiautomator dump`
 */
// uiautomator has no option to output to STDOUT so we dump it to a temp file and clean it up.
internal val TMP_DUMP_FILE = "/sdcard/studio_bot_window_dump.xml"
internal val DUMP_COMMAND = "uiautomator dump $TMP_DUMP_FILE"
internal val READ_COMMAND = "cat $TMP_DUMP_FILE"
internal val CLEANUP_COMMAND = "rm -f $TMP_DUMP_FILE &> /dev/null"

data class Region(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val name: String)
data class UiState(val xml: String = "", val nafRegions: List<Region> = emptyList(), val error: String? = null) {
  fun hasError() = error != null
}

class ShellCommandUiDumpProvider {
  suspend fun uiDump(project: Project, serialNumber: String) : UiState {

    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val adbLibService = AdbLibService.getInstance(project)

    var shellOutput =
      adbLibService.session.deviceServices.shellAsText(deviceSelector, DUMP_COMMAND)

    if (shellOutput.exitCode != 0) {
      return UiState(error = "$DUMP_COMMAND failed with exit code ${shellOutput.exitCode}. ${shellOutput.stderr}")
    }

    shellOutput = adbLibService.session.deviceServices.shellAsText(deviceSelector, READ_COMMAND)

    if (shellOutput.exitCode != 0) {
      return UiState(error = "Failed to read $TMP_DUMP_FILE. ${shellOutput.stderr}")
    }

    val result = shellOutput.stdout

    shellOutput = adbLibService.session.deviceServices.shellAsText(deviceSelector, CLEANUP_COMMAND)

    if (shellOutput.exitCode != 0) {
      thisLogger().warn("Failed to clean up $DUMP_COMMAND: ${shellOutput.stderr}")
    }

    return postProcess(result)
  }
}