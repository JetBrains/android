/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common.adb.shell.tasks

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidExecutionException
import java.util.regex.Pattern

/**
 * Starts the sandbox sdk
 *
 * If the sandbox apk is installed on the device, executes `cmd sdk_sandbox start <package>`.
 */
fun launchSandboxSdk(device: IDevice, clientAppPackage: String) {
  val collectingOutputReceiver = CollectingOutputReceiver()

  device.executeShellCommand(getCommand(clientAppPackage), collectingOutputReceiver)
  for (pattern in errorPatterns) {
    val matcher = pattern.value.matcher(collectingOutputReceiver.output)
    if (matcher.find()) {
      throw AndroidExecutionException(pattern.key, matcher.group())
    }
  }
}

private fun getCommand(clientAppPackage: String): String = "cmd sdk_sandbox start $clientAppPackage"

const val SANDBOX_IS_DISABLED = "(.*)SDK sandbox is disabled(.*)"
const val PACKAGE_NOT_FOUND = "(.*)No such package [^ ]+ for user [0-9]+(.*)"
private val errorPatterns = mapOf<String, Pattern>(
  SANDBOX_IS_DISABLED to Pattern.compile(SANDBOX_IS_DISABLED), PACKAGE_NOT_FOUND to Pattern.compile(PACKAGE_NOT_FOUND)
)
