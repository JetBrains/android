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
package com.android.tools.idea.ui.screenrecording

import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache.Key
import com.android.adblib.DevicePropertyNames.RO_BUILD_CHARACTERISTICS
import com.android.adblib.DeviceSelector
import com.android.adblib.deviceCache
import com.android.adblib.shellAsText
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import java.time.Duration

private const val SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord"
private val COMMAND_TIMEOUT = Duration.ofSeconds(2)
private val IS_SUPPORTED_RETRY_TIMEOUT = Duration.ofSeconds(2)

/**
 * A cache of mapping of a device to a boolean indicating if it supports screen recording.
 *
 * TODO(b/235094713): Add tests
 */
internal class ScreenRecordingSupportedCacheImpl(project: Project) : ScreenRecordingSupportedCache {
  private val adbSession: AdbSession = AdbLibService.getSession(project)
  private val cacheKey = Key<Boolean>("ScreenRecordingSupportedCache")

  override fun isScreenRecordingSupported(serialNumber: String, sdk: Int): Boolean {
    return adbSession.deviceCache(serialNumber).getOrPutSuspending(
        cacheKey, fastDefaultValue = { false }, defaultValue = { computeIsSupported(serialNumber, sdk) })
  }

  private suspend fun computeIsSupported(serialNumber: String, sdk: Int): Boolean {
    // The default value (from the cache) is "false" until this function terminates,
    // so we try every 2 seconds until we can answer without error.
    while (true) {
      try {
        return when {
          serialNumber.isEmulator() -> true
          isWatch(serialNumber) && sdk < 30 -> false
          sdk < 19 -> false
          else -> hasBinary(serialNumber)
        }
      }
      catch (e: Throwable) {
        thisLogger().warn("Failure to retrieve screen recording support status for device $serialNumber, retrying in 2 seconds", e)
        delay(IS_SUPPORTED_RETRY_TIMEOUT.toMillis())
      }
    }
  }

  private suspend fun isWatch(serialNumber: String): Boolean {
    val out = execute(serialNumber, "getprop $RO_BUILD_CHARACTERISTICS")
    return out.trim().split(",").contains("watch")
  }

  private suspend fun hasBinary(serialNumber: String): Boolean {
    val out = execute(serialNumber, "ls $SCREEN_RECORDER_DEVICE_PATH")
    return out.trim() == SCREEN_RECORDER_DEVICE_PATH
  }

  private suspend fun execute(serialNumber: String, command: String): String =
    //TODO: Check for `stderr` and `exitCode` to report errors
    adbSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber), command, commandTimeout = COMMAND_TIMEOUT).stdout
}

private fun String.isEmulator() = startsWith("emulator-")
