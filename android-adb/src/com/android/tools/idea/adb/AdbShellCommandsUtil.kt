/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.adb

import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.utils.TextShellCollector
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.IDevice
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import com.android.tools.idea.adblib.ddmlibcompatibility.defaultDdmTimeoutMillis
import com.android.tools.idea.adblib.ddmlibcompatibility.deviceServices
import com.android.tools.idea.adblib.ddmlibcompatibility.executeShellCommand
import com.android.tools.idea.adblib.ddmlibcompatibility.toDeviceSelector
import com.google.common.base.Stopwatch
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.time.Duration

abstract class AdbShellCommandsUtil {
  private val logger = thisLogger()

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun executeCommandBlocking(command: String): AdbShellCommandResult {
    return runBlocking {
      executeCommandImpl(command, true)
    }
  }

  suspend fun executeCommand(command: String): AdbShellCommandResult {
    return executeCommandImpl(command, true)
  }

  suspend fun executeCommandNoErrorCheck(command: String): AdbShellCommandResult {
    return executeCommandImpl(command, false)
  }

  private suspend fun executeCommandImpl(command: String, errorCheck: Boolean): AdbShellCommandResult {
    // Adding the " || echo xxx" command to the command allows us to detect non-zero status code
    // from the command by analysing the output and looking for the "xxx" marker.
    val fullCommand = if (errorCheck) command + COMMAND_ERROR_CHECK_SUFFIX else command
    val commandOutput: MutableList<String> = ArrayList()
    val stopwatch = Stopwatch.createStarted()
    val receiver = TextShellCollector()
    commandOutput.addAll(executeCommandImpl(fullCommand, receiver).single().split("\r\n", "\n"))
    logger.info("Command took $stopwatch to execute: $command")

    // Look for error marker in the last 2 output lines
    var isError = false
    if (errorCheck && commandOutput.size >= 2 &&
      commandOutput[commandOutput.size - 2] == ERROR_LINE_MARKER &&
      commandOutput[commandOutput.size - 1] == ""
    ) {
      isError = true
      commandOutput.removeLast()
      commandOutput.removeLast()
    }

    // Log first two lines of the output for diagnostic purposes
    if (commandOutput.size >= 1) {
      logger.info("  Output line 1 (out of ${commandOutput.size}): ${commandOutput[0]}")
    }
    if (commandOutput.size >= 2) {
      logger.info("  Output line 2 (out of ${commandOutput.size}): ${commandOutput[1]}")
    }
    if (logger.isDebugEnabled) {
      for (i in 2 until commandOutput.size) {
        logger.debug("  Output line ${i + 1} (out of ${commandOutput.size}): ${commandOutput[i]}")
      }
    }

    return AdbShellCommandResult(command, commandOutput, isError)
  }

  abstract fun <T> executeCommandImpl(command: String, receiver: ShellCollector<T>): Flow<T>

  companion object {
    private const val ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR"
    private const val COMMAND_ERROR_CHECK_SUFFIX = " || echo $ERROR_LINE_MARKER"

    @JvmStatic
    fun create(device: IDevice, useAdbLib: Boolean) =
      if (useAdbLib) create(device.toDeviceSelector()) else create(device)

    @JvmStatic
    fun create(device: IDevice) = object : AdbShellCommandsUtil() {
      override fun <T> executeCommandImpl(command: String, receiver: ShellCollector<T>): Flow<T> =
        executeShellCommand(device, command, receiver)
    }

    @JvmStatic
    fun create(device: DeviceSelector) = object : AdbShellCommandsUtil() {
      override fun <T> executeCommandImpl(command: String, receiver: ShellCollector<T>): Flow<T> =
        deviceServices.shell(device, command, receiver, commandTimeout = Duration.ofMillis(defaultDdmTimeoutMillis))
    }
  }
}