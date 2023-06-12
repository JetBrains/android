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
package com.android.tools.idea.appinspection.internal.process

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.diagnostic.Logger

// TODO: Use the information from the HELO message when this is available for newer API levels
fun Common.Device.isDebuggable(processName: String): Boolean {
  // If this is not a userdebug build we trust that non-debuggable processes are not visible
  if (buildType != "userdebug") {
    return true
  }

  // This is a userdebug build, check the package to make sure the process itself is debuggable
  val device = findDevice() ?: return false
  return device.isPackageDebuggable(processName)
}

fun IDevice.isPackageDebuggable(packageName: String): Boolean {
  val receiver = SingleDebuggableProcessReceiver()
  try {
    executeShellCommand("dumpsys package $packageName", receiver)
  } catch (ex: Exception) {
    Logger.getInstance(TransportDeviceManager::class.java).warn(ex)
  }
  return receiver.packageName == packageName && receiver.isDebuggable
}

private class SingleDebuggableProcessReceiver : MultiLineReceiver() {
  var isDebuggable = false
    private set
  var packageName = ""
    private set

  private val packageRegex = "^Package \\[([\\w.]+)] \\(\\w+\\):$".toRegex()
  private val flagsRegex = "^flags=\\[ ([\\w\\s_]+) ]$".toRegex()
  private var done = false

  override fun processNewLines(lines: Array<out String>) {
    for (line in lines) {
      if (packageName.isEmpty()) {
        val packageResult = packageRegex.matchEntire(line)
        packageResult?.let { packageName = it.groupValues[1] }
      } else {
        val result = flagsRegex.matchEntire(line)
        result?.let {
          val flags = it.groupValues[1].split(' ')
          if (flags.contains("DEBUGGABLE")) {
            isDebuggable = true
          }
          done = true
          return
        }
      }
    }
  }

  override fun isCancelled(): Boolean = done
}

private fun Common.Device.findDevice(): IDevice? =
  AndroidDebugBridge.getBridge()?.devices?.find { serial == it.serialNumber }
