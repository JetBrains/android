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
import com.intellij.openapi.project.Project

/**
 * Mostly just return `adb shell uiautomator dump`
 */
// uiautomator has no option to output to STDOUT so we dump it to a temp file and clean it up.
internal val COMMANDLINE =
  "uiautomator dump /tmp/studio_bot_window_dump.xml &> /dev/null;" +
  " cat /tmp/studio_bot_window_dump.xml; "+
  " rm -f /tmp/studio_bot_window_dump.xml &> /dev/null"

class ShellCommandUiDumpProvider {
  suspend fun uiDump(project: Project, serialNumber: String) : String {

    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val adbLibService = AdbLibService.getInstance(project)
    val shell = adbLibService.session.deviceServices.shellCommand(deviceSelector, COMMANDLINE)

    val stdoutBuilder = StringBuilder()
    shell.withTextCollector().execute().collect { value -> stdoutBuilder.append(value.stdout) }
    return stdoutBuilder.toString()
  }
}