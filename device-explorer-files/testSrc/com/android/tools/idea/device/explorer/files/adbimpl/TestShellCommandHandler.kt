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
import com.android.fakeadbserver.shellcommandhandlers.ShellHandler
import java.lang.Thread.sleep
import java.net.Socket

/**
 * Handler for FakeAdbServer that handles shell commands using the given TestShellCommands
 * (i.e. predefined commands and responses).
 */
class TestShellCommandHandler(val shellCommands: TestShellCommands) : ShellHandler() {
  override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
    if (command == "shell" && shellCommands.get(args) != null) {
      invoke(server, socket, device, args)
      return true
    }
    return false
  }

  override fun invoke(server: FakeAdbServer, socket: Socket, device: DeviceState, args: String) {
    val result = shellCommands.get(args)
    val outputStream = socket.getOutputStream()
    if (result == null) {
      writeFail(outputStream)
      writeString(outputStream, "Command not configured")
      return
    }

    when (result.error) {
      is ShellCommandUnresponsiveException -> {
        writeOkay(outputStream)
        writeString(outputStream, "Starting output...")
        sleep(DdmPreferences.getTimeOut() + 1000L)
        return
      }
      null -> {
        writeOkay(outputStream)
        writeString(outputStream, checkNotNull(result.output))
      }
      else -> {
        writeFail(outputStream)
        writeString(outputStream, result.error.toString())
      }
    }
  }
}