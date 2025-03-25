/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbSession
import com.android.adblib.AdbUsageTracker
import com.android.adblib.AdbUsageTracker.AdbDeviceStateChangeEvent
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceState
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.tools.idea.isAndroidEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

class DeviceStateReporter : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!isAndroidEnvironment(project)) {
      return
    }

    val session = AdbLibService.getInstance(project).session
    val timeProvider = session.host.timeProvider
    session.scope.launch {
      // These maps are never cleared. This shouldn't be a problem since the number
      // of devices in use is generally limited.
      val lastOnlineByDeviceSerial = mutableMapOf<String, Long>()
      val previousStateByDeviceSerial = mutableMapOf<String, DeviceState>()

      session.deviceInfoChangeFlow(AdbUsageTracker.DeviceInfo::createFrom).collect {
        (deviceState, deviceSerial, calculationResult) ->
        var timeSinceLastOnlineNs =
          lastOnlineByDeviceSerial[deviceSerial]?.let { timeProvider.nanoTime() - it }

        val previousDeviceState = previousStateByDeviceSerial[deviceSerial]
        // Device goes offline (i.e. stops being online)
        if (previousDeviceState == DeviceState.ONLINE) {
          lastOnlineByDeviceSerial[deviceSerial] = timeProvider.nanoTime()
          timeSinceLastOnlineNs = 0
        }
        previousStateByDeviceSerial[deviceSerial] = deviceState

        session.host.usageTracker.logUsage(
          AdbUsageTracker.Event(
            deviceInfo = calculationResult,
            adbDeviceStateChange =
              AdbDeviceStateChangeEvent(
                deviceState = deviceState.toUsageTrackerDeviceState(),
                previousDeviceState = previousDeviceState?.toUsageTrackerDeviceState(),
                lastOnlineMs = timeSinceLastOnlineNs?.div(1_000_000),
              ),
          )
        )
      }
    }
  }

  private fun DeviceState.toUsageTrackerDeviceState(): AdbUsageTracker.DeviceState {
    return when (this) {
      DeviceState.BOOTLOADER -> AdbUsageTracker.DeviceState.BOOTLOADER
      DeviceState.AUTHORIZING -> AdbUsageTracker.DeviceState.AUTHORIZING
      DeviceState.CONNECTING -> AdbUsageTracker.DeviceState.CONNECTING
      DeviceState.ONLINE -> AdbUsageTracker.DeviceState.ONLINE
      DeviceState.OFFLINE -> AdbUsageTracker.DeviceState.OFFLINE
      DeviceState.DISCONNECTED -> AdbUsageTracker.DeviceState.DISCONNECTED
      else -> AdbUsageTracker.DeviceState.OTHER
    }
  }

  private data class DeviceStateChange<T>(
    val deviceState: DeviceState,
    val deviceSerial: String,
    val calculationResult: T?,
  )

  /**
   * This flow can be used to keep track of the connected devices and their state transitions such
   * as devices coming online and eventually disconnecting.
   *
   * @param calculation calculate additional data. Caches it when the data is calculated for the
   *   device that is in ONLINE state (this is done because some properties are available only when
   *   the device goes online, and we want the result of this calculation to be available even when
   *   the device goes offline)
   */
  private fun <T> AdbSession.deviceInfoChangeFlow(
    calculation: suspend (ConnectedDevice) -> T
  ): Flow<DeviceStateChange<T>> = channelFlow {
    val allConnectedDevices: MutableSet<ConnectedDevice> =
      Collections.newSetFromMap(IdentityHashMap())
    val deviceInfoTrackingJobs = IdentityHashMap<ConnectedDevice, Job>()
    val whenOnlineCalculationCache = ConcurrentHashMap<ConnectedDevice, T>()
    connectedDevicesTracker.connectedDevices.collect { value ->
      run {
        // Process added devices
        val addedDevices = value.filter { !allConnectedDevices.contains(it) }
        allConnectedDevices.addAll(addedDevices)

        // Process removed devices
        val removedDevices = allConnectedDevices.filter { !value.contains(it) }.toSet()
        allConnectedDevices.removeAll(removedDevices)
        for (removedDevice in removedDevices) {
          deviceInfoTrackingJobs.remove(removedDevice)?.also {
            it.cancel(
              "Cancelling DeviceInfo tracking for removed device [${removedDevice.serialNumber}]"
            )
            it.join()
            // DISCONNECTED value is emitted from here rather than from `deviceInfoFlow` collector
            // since cancelling the `deviceInfoTrackingJob` sometimes results in DISCONNECTED value
            // being skipped.
            send(
              DeviceStateChange(
                DeviceState.DISCONNECTED,
                removedDevice.serialNumber,
                whenOnlineCalculationCache[removedDevice],
              )
            )
            whenOnlineCalculationCache.remove(removedDevice)
          }
        }

        for (addedConnectedDevice in addedDevices) {
          deviceInfoTrackingJobs[addedConnectedDevice] = launch {
            addedConnectedDevice.deviceInfoFlow
              .takeWhile { it.deviceState != DeviceState.DISCONNECTED }
              .collect { deviceInfo ->
                // Try to use the result from the cache (if available).
                // Otherwise, runs the calculation and returns
                // the result (also storing it in the cache if the device is online)
                val cachedValue =
                  if (addedConnectedDevice.isOnline) {
                    whenOnlineCalculationCache.getOrPut(addedConnectedDevice) {
                      calculation(addedConnectedDevice)
                    }
                  } else {
                    whenOnlineCalculationCache[addedConnectedDevice]
                      ?: calculation(addedConnectedDevice)
                  }
                send(
                  DeviceStateChange(deviceInfo.deviceState, deviceInfo.serialNumber, cachedValue)
                )
              }
          }
        }
      }
    }
  }
}
