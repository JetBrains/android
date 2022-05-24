/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib.utils

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsLines
import kotlinx.coroutines.flow.toList

/**
 * A single line property
 */
private val SingleLinePattern = Regex("^\\[([^]]+)]:\\s*\\[(.*)]$") //$NON-NLS-1$

/** Two patterns in case the property span several lines.  */
private val StartLinePattern = Regex("^\\[([^]]+)]:\\s*\\[(.*)$") //$NON-NLS-1$

private val EndLinePattern = Regex("(.*)]$") //$NON-NLS-1$

/**
 * Parser to process the result of a device `getprop` shell command output into a [List] of [DeviceProperty] entries.
 *
 * * Some properties are single line, e.g.
 *         [foo.bar] = [blah]
 *
 * * Some properties span multiple lines, e.g.
 *         [foo.bar] = [line 1\n
 *         line 2\n
 *         line 3]
 */
class DevicePropertiesParser {

  fun parse(lines: Sequence<String>): List<DeviceProperty> {
    val result = ArrayList<DeviceProperty>()
    val iterator = lines.iterator()
    while (iterator.hasNext()) {
      matchOneEntry(iterator)?.let { result.add(it) }
    }

    return result
  }

  private fun matchOneEntry(iterator: Iterator<String>): DeviceProperty? {
    val line = iterator.next()
    return matchOneLine(line) ?: matchMultiLine(line, iterator)
  }

  fun matchOneLine(line: String): DeviceProperty? {
    val matchResult = SingleLinePattern.matchEntire(line)
    return matchResult?.let {
      DeviceProperty(matchResult.groupValues[1], matchResult.groupValues[2])
    }
  }

  private fun matchMultiLine(line: String, iterator: Iterator<String>): DeviceProperty? {
    val matchResult = StartLinePattern.matchEntire(line)
    return matchResult?.let {
      val propName = matchResult.groupValues[1]
      val sb = StringBuilder()
      sb.append(matchResult.groupValues[2])
      while (iterator.hasNext()) {
        val nextLine = iterator.next()
        sb.append("\n")
        val endLineMatchResult = EndLinePattern.matchEntire(nextLine)
        if (endLineMatchResult == null) {
          sb.append(nextLine)
        }
        else {
          sb.append(endLineMatchResult.groupValues[1])
          break
        }
      }
      DeviceProperty(propName, sb.toString())
    }
  }

  companion object {
    /**
     * The shell command to run on the device
     */
    const val ShellCommand = "getprop" //$NON-NLS-1$
  }
}

data class DeviceProperty(val name: String, val value: String)

/**
 * Execute a `getprop` shell command on [device] and returns a [List] of [DeviceProperty] entries
 */
suspend fun AdbDeviceServices.getprop(device: DeviceSelector) : List<DeviceProperty> {
  val lines = shellAsLines(device, DevicePropertiesParser.ShellCommand).toList()
  return DevicePropertiesParser().parse(lines.asSequence())
}
