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
import com.android.adblib.DeviceSelector
import com.android.adblib.deviceInfo
import com.android.adblib.shellAsText
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import java.time.Duration

private const val SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord"
private val COMMAND_TIMEOUT = Duration.ofSeconds(5)
private val IS_SUPPORTED_RETRY_TIMEOUT = Duration.ofSeconds(2)

/**
 * A cache of mapping of a device to a boolean indicating if it supports screen recording.
 *
 * TODO(b/235094713): Add tests
 */
internal class ScreenRecordingSupportedCacheImpl(project: Project) : ScreenRecordingSupportedCache {
  private val adbSession: AdbSession = AdbLibService.getSession(project)
  private val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
  private val cacheKey = Key<Boolean>("ScreenRecordingSupportedCache")

  override fun isScreenRecordingSupported(serialNumber: String): Boolean {
    val connectedDeviceState: DeviceState.Connected = deviceProvisioner.findConnectedDevice(serialNumber) ?: return false
    val properties = connectedDeviceState.properties

    if (properties.isVirtual == true) {
      return true
    }
    val api = properties.androidVersion?.apiLevel ?: SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
    if (api < 19) {
      return false
    }
    if (properties.deviceType == DeviceType.WEAR && api < 30) {
      return false
    }
    if (properties.manufacturer == "Google" || properties.manufacturer == "Samsung") {
      return true // Reputable vendors support screen recording.
    }
    return connectedDeviceState.connectedDevice.cache.getOrPutSuspending(
        cacheKey, fastDefaultValue = { true }, defaultValue = { computeIsSupported(serialNumber) })
  }

  private suspend fun computeIsSupported(serialNumber: String): Boolean {
    // The default value (from the cache) is true until this function terminates,
    // so we try every 2 seconds until we can answer without error.
    while (true) {
      try {
        val out = execute(serialNumber, "ls $SCREEN_RECORDER_DEVICE_PATH")
        return out.trim() == SCREEN_RECORDER_DEVICE_PATH
      }
      catch (e: Throwable) {
        thisLogger().warn("Failure to retrieve screen recording support status for device $serialNumber, retrying in 2 seconds", e)
        delay(IS_SUPPORTED_RETRY_TIMEOUT.toMillis())
      }
    }
  }

  private suspend fun execute(serialNumber: String, command: String): String =
      adbSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber), command, commandTimeout = COMMAND_TIMEOUT).stdout
}

private fun DeviceProvisioner.findConnectedDevice(serialNumber: String): DeviceState.Connected? =
    devices.value.firstOrNull { it.state.connectedDevice?.deviceInfo?.serialNumber == serialNumber }?.state as? DeviceState.Connected

