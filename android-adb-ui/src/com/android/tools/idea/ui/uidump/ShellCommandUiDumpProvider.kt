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
import com.android.adblib.shellCommand
import com.android.adblib.withTextCollector
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

class ShellCommandUiDumpProvider {
  suspend fun uiDump(project: Project, serialNumber: String) : String {

    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val adbLibService = AdbLibService.getInstance(project)

    var shell = adbLibService.session.deviceServices.shellCommand(deviceSelector, DUMP_COMMAND)
    var stdoutBuilder = StringBuilder()
    var stderrBuilder = StringBuilder()
    var exitCode = 0

    shell.withTextCollector().execute().collect {value ->
      stderrBuilder.append(value.stderr)
      exitCode = value.exitCode
    }

    if (exitCode != 0) {
      return "$DUMP_COMMAND failed with exit code $exitCode. $stderrBuilder"
    }

    stdoutBuilder = StringBuilder()
    stderrBuilder = StringBuilder()
    exitCode = 0
    shell = adbLibService.session.deviceServices.shellCommand(deviceSelector, READ_COMMAND)
    shell.withTextCollector().execute().collect {value ->
      stdoutBuilder.append(value.stdout)
      stderrBuilder.append(value.stderr)
      exitCode = value.exitCode
    }

    if (exitCode != 0) {
      return "Failed to read $TMP_DUMP_FILE. $stderrBuilder"
    }

    val result = stdoutBuilder.toString()

    stdoutBuilder = StringBuilder()
    stderrBuilder = StringBuilder()
    exitCode = 0
    shell = adbLibService.session.deviceServices.shellCommand(deviceSelector, CLEANUP_COMMAND)

    shell.withTextCollector().execute().collect {value ->
      stderrBuilder.append(value.stderr)
      exitCode = value.exitCode
    }

    if (exitCode != 0) {
      thisLogger().warn("Failed to clean up $DUMP_COMMAND: $stderrBuilder")
    }

    return result
  }
}