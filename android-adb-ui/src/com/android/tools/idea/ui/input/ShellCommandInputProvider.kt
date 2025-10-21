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
package com.android.tools.idea.ui.input

import com.android.adblib.DeviceSelector
import com.android.adblib.shellCommand
import com.android.adblib.withTextCollector
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.project.Project


/**
 * Mostly just return `input [<source>] [-d DISPLAY_ID] <command> [<arg>...]`
 * but correcting some common mistakes by the LLM.
 */
class ShellCommandInputProvider {
  suspend fun input(project: Project, serialNumber: String,
                    source: String, displayID: Int, command: String, args: List<String>): String {

    val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
    val adbLibService = AdbLibService.getInstance(project)

    val shell = adbLibService.session.deviceServices
      .shellCommand(deviceSelector, "input $source -d $displayID ${getCommandLine(command, args)}")
    val stdoutBuilder = StringBuilder()
    val stderrBuilder = StringBuilder()
    var exitCode = 0

    shell.withTextCollector().execute().collect {value ->
      stdoutBuilder.append(value.stdout)
      stderrBuilder.append(value.stderr)
      exitCode = value.exitCode
    }

    if (exitCode == 0) {
      return stdoutBuilder.toString()
    } else {
      return "Command exited with $exitCode. $stderrBuilder"
    }
  }
}

internal fun getCommandLine(command: String, args: List<String>) : String {
  return when (command) {
    "text" -> {
      if (args.size == 1) {
        val arg = args.single()
        if ((arg.startsWith('\'') && arg.endsWith('\''))  ||
            (arg.startsWith('"') && arg.endsWith('"')) ) {
          "text $arg"
        } else {
          "text ${arg.replace(" ", "%s")}"
        }
      } else {
        "text ${args.joinToString(" ").replace(" ", "%s")}"
      }
    }
    else -> "$command ${args.joinToString(" ")}"
  }
}