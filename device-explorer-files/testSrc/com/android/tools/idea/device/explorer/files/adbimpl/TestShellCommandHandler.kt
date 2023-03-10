/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ServiceOutput
import com.android.fakeadbserver.shellv2commandhandlers.ShellV2Handler
import com.android.fakeadbserver.shellv2commandhandlers.StatusWriter
import java.lang.Thread.sleep

/**
 * Handler for FakeAdbServer that handles shell commands using the given TestShellCommands
 * (i.e. predefined commands and responses).
 */
class TestShellCommandHandler(shellProtocolType: ShellProtocolType, val shellCommands: TestShellCommands) : ShellV2Handler(
  shellProtocolType) {

  override fun shouldExecute(shellCommand: String, shellCommandArgs: String?): Boolean {
    return shellCommands.get("$shellCommand $shellCommandArgs") != null
  }

  override fun execute(fakeAdbServer: FakeAdbServer,
                       statusWriter: StatusWriter,
                       serviceOutput: ServiceOutput,
                       device: DeviceState,
                       shellCommand: String,
                       shellCommandArgs: String?) {
    val result = shellCommands.get("$shellCommand $shellCommandArgs")
    assert(result != null)

    when (result.error) {
      is ShellCommandUnresponsiveException -> {
        statusWriter.writeOk()
        serviceOutput.writeStdout("Starting output...")
        sleep(DdmPreferences.getTimeOut() + 1000L)
        return
      }
      null -> {
        statusWriter.writeOk()
        serviceOutput.writeStdout(checkNotNull(result.output))
      }
      else -> {
        statusWriter.writeFail()
        serviceOutput.writeStdout(result.error.toString())
      }
    }
  }
}