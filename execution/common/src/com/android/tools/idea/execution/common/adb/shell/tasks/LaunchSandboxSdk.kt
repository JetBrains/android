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
import com.intellij.openapi.diagnostic.Logger
import java.util.regex.Pattern

/**
 * Starts the sandbox sdk
 *
 * If the sandbox apk is installed on the device, executes `cmd sdk_sandbox start <package>`.
 */
fun launchSandboxSdk(device: IDevice, clientAppPackage: String, logger: Logger) {
  // Stop any previous sdk processes. It could error if the process is not found which we can ignore.
  val stopCommandReceiver = CollectingOutputReceiver()
  device.executeShellCommand(getCommandStopSdkSandbox(clientAppPackage), stopCommandReceiver)
  if (stopCommandReceiver.output.isNotEmpty()) { logger.debug(stopCommandReceiver.output) }

  // Temp allowlist command shouldn't return anything useful.
  val allowCommandReceiver = CollectingOutputReceiver()
  device.executeShellCommand(getCommandAllowList(clientAppPackage), allowCommandReceiver)
  if (allowCommandReceiver.output.isNotEmpty()) { logger.debug(allowCommandReceiver.output) }

  val startCommandReceiver = CollectingOutputReceiver()
  device.executeShellCommand(getCommandStartSdkSandbox(clientAppPackage), startCommandReceiver)
  for (pattern in errorPatterns) {
    val matcher = pattern.value.matcher(startCommandReceiver.output)
    if (matcher.find()) {
      throw AndroidExecutionException(pattern.key, matcher.group())
    }
  }
  if (startCommandReceiver.output.isNotEmpty()) { logger.debug(startCommandReceiver.output) }
}

// Need to split command string because it doesn't use inclusive language
fun getCommandAllowList(clientAppPackage: String): String = "cmd deviceidle tempwhite" + "list $clientAppPackage"

fun getCommandStartSdkSandbox(clientAppPackage: String): String = "cmd sdk_sandbox start $clientAppPackage"

fun getCommandStopSdkSandbox(clientAppPackage: String): String = "cmd sdk_sandbox stop $clientAppPackage"

const val SANDBOX_IS_DISABLED = "(.*)SDK sandbox is disabled(.*)"
const val PACKAGE_NOT_FOUND = "(.*)No such package [^ ]+ for user [0-9]+(.*)"
const val BACKGROUND_START_NOT_ALLOWED = "(.*)android.app.BackgroundServiceStartNotAllowedException(.*)"
const val SANDBOX_SDK_ALREADY_RUNNING = "(.*)Sdk sandbox already running for(.*)"
const val FAILED_TO_START = "(.*)Sdk sandbox failed to start in [0-9]+ seconds(.*)"
const val PACKAGE_NOT_DEBUGGABLE = "(.*)Package [^ ]+ must be debuggable(.*)"

private val errorPatterns = mapOf<String, Pattern>(
  SANDBOX_IS_DISABLED to Pattern.compile(SANDBOX_IS_DISABLED),
  PACKAGE_NOT_FOUND to Pattern.compile(PACKAGE_NOT_FOUND),
  BACKGROUND_START_NOT_ALLOWED to Pattern.compile(BACKGROUND_START_NOT_ALLOWED),
  SANDBOX_SDK_ALREADY_RUNNING to Pattern.compile(SANDBOX_SDK_ALREADY_RUNNING),
  FAILED_TO_START to Pattern.compile(FAILED_TO_START),
  PACKAGE_NOT_DEBUGGABLE to Pattern.compile(PACKAGE_NOT_DEBUGGABLE)
)
