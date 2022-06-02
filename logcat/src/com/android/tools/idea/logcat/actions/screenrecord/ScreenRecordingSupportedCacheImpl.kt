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
package com.android.tools.idea.logcat.actions.screenrecord

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.createDeviceScope
import com.android.adblib.shellAsText
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.EmulatorAdvFeatures
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.utils.ILogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord"
private val COMMAND_TIMEOUT = Duration.ofSeconds(2)

/**
 * A cache of mapping of a device to a boolean indicating if it supports screen recording.
 *
 * TODO(b/235094713): Add tests
 */
internal class ScreenRecordingSupportedCacheImpl(private val project: Project) : ScreenRecordingSupportedCache {
  private val cache = ConcurrentHashMap<String, Entry>()
  private val adbLibSession: AdbLibSession = AdbLibService.getSession(project)

  @UiThread
  override fun isScreenRecordingSupported(serialNumber: String, sdk: Int): Boolean {
    val currentEntry = cache[serialNumber]
    if (currentEntry is Entry.Computing) {
      // Another call is still computing, return "false" until we know
      return false
    }
    if (currentEntry is Entry.Result) {
      // We already computed the result for this device, return it
      return currentEntry.value
    }

    // Mark as "Computing", return false if someone else is already computing
    val computing = Entry.Computing()
    if (cache.putIfAbsent(serialNumber, computing) != null) {
      return false
    }

    // Compute
    val deviceScope = adbLibSession.createDeviceScope(DeviceSelector.fromSerialNumber(serialNumber))
    deviceScope.launch {
      val isSupported = try {
        when {
          serialNumber.isEmulator() && isEmulatorRecordingEnabled() -> true
          isWatch(serialNumber) && sdk < 30 -> false
          sdk < 19 -> false
          else -> hasBinary(serialNumber)
        }
      }
      catch (e: Throwable) {
        cache.remove(serialNumber, computing)
        thisLogger().debug("Failure to compute for device $serialNumber", e)
        return@launch
      }
      val supported = Entry.Result(isSupported)
      cache.replace(serialNumber, computing, supported)

      // Invalidate cache when device is disconnected
      deviceScope.coroutineContext.job.invokeOnCompletion {
        // Remove only if value has been generated from this call
        cache.remove(serialNumber, supported)
        thisLogger().debug("Removed $serialNumber from cache")
      }
    }
    return false
  }

  private suspend fun isWatch(serialNumber: String): Boolean {
    val out = execute(serialNumber, "getprop ro.build.characteristics")
    return out.trim().split(",").contains("watch")
  }

  private suspend fun hasBinary(serialNumber: String): Boolean {
    val out = execute(serialNumber, "ls $SCREEN_RECORDER_DEVICE_PATH")
    return out.trim() == SCREEN_RECORDER_DEVICE_PATH
  }

  private suspend fun execute(serialNumber: String, command: String) =
    adbLibSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber), command, commandTimeout = COMMAND_TIMEOUT)

  private sealed class Entry {
    @Suppress("CanSealedSubClassBeObject")
    class Computing : Entry() {
      override fun toString() = "Computing"
    }

    class Result(val value: Boolean) : Entry() {
      override fun toString() = "Result($value, ${hashCode()})"
    }
  }
}

private fun String.isEmulator() = startsWith("emulator-")

private fun isEmulatorRecordingEnabled(): Boolean {
  val handler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
  val indicator = StudioLoggerProgressIndicator(ScreenRecorderAction::class.java)
  val logger: ILogger = LogWrapper(ScreenRecorderAction::class.java)
  return EmulatorAdvFeatures.emulatorSupportsScreenRecording(handler, indicator, logger)
}
