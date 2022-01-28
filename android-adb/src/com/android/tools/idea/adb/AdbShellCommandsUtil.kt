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

import com.android.tools.idea.adblib.ddmlibcompatibility.executeShellCommand
import kotlin.Throws
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import java.io.IOException
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.TimeoutException
import com.google.common.base.Stopwatch
import com.intellij.openapi.diagnostic.thisLogger
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale

class AdbShellCommandsUtil(private val myUseAdbLib: Boolean) {
  private val logger = thisLogger()

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun executeCommand(device: IDevice, command: String): AdbShellCommandResult {
    return executeCommandImpl(device, command, true)
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun executeCommandNoErrorCheck(device: IDevice, command: String): AdbShellCommandResult {
    return executeCommandImpl(device, command, false)
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun executeRawCommand(device: IDevice, command: String, receiver: IShellOutputReceiver) {
    val stopwatch = Stopwatch.createStarted()
    executeCommandImpl(device, command, receiver)
    if (logger.isTraceEnabled) {
      logger.trace("Command took $stopwatch to execute: $command")
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun executeCommandImpl(device: IDevice, command: String, errorCheck: Boolean): AdbShellCommandResult {
    val commandOutput: MutableList<String> = ArrayList()
    // Adding the " || echo xxx" command to the command allows us to detect non-zero status code
    // from the command by analysing the output and looking for the "xxx" marker.
    val fullCommand = if (errorCheck) command + COMMAND_ERROR_CHECK_SUFFIX else command
    val receiver: MultiLineReceiver = object : MultiLineReceiver() {
      override fun processNewLines(lines: Array<String>) {
        commandOutput.addAll(lines)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    }
    receiver.setTrimLine(false)
    executeCommandImpl(device, fullCommand, receiver)

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

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun executeCommandImpl(device: IDevice, command: String, receiver: IShellOutputReceiver) {
    val stopwatch = Stopwatch.createStarted()
    if (myUseAdbLib) {
      executeShellCommand(device, command, receiver)
    } else {
      device.executeShellCommand(command, receiver)
    }
    logger.info("Command took $stopwatch to execute: $command")
  }

  companion object {
    private const val ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR"
    private const val COMMAND_ERROR_CHECK_SUFFIX = " || echo $ERROR_LINE_MARKER"

    @JvmStatic
    val instance = AdbShellCommandsUtil(false)
  }
}