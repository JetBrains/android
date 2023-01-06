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
package com.android.tools.idea.logcat.devices

import com.android.adblib.AdbSession
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DeviceState.ONLINE
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.logcat.devices.DeviceEvent.TrackingReset
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * An implementation of IDeviceComboBoxDeviceTracker that uses an [AdbSession]
 */
internal class DeviceComboBoxDeviceTracker(
  project: Project,
  private val preexistingDevice: Device?,
  private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : IDeviceComboBoxDeviceTracker {

  private val adbSession: AdbSession = AdbLibService.getSession(project)

  override suspend fun trackDevices(
    /* For test only. Temporary until we switch to the new trackDevices API */ retryOnException: Boolean
  ): Flow<DeviceEvent> {
    return flow {
      while (true) {
        // TODO(b/228224334): This should be handled internally by AdbLib
        try {
          trackDevicesInternal()
        }
        catch (e: IOException) {
          emit(TrackingReset(e))
          if (retryOnException) {
            LOGGER.info("Device tracker exception, restarting it...", e)
            continue
          }
        }
        break
      }
    }.flowOn(coroutineContext)
  }

  private suspend fun FlowCollector<DeviceEvent>.trackDevicesInternal() {
    val onlineDevicesBySerial = mutableMapOf<String, Device>()
    val allDevicesById = mutableMapOf<String, Device>()
    val deviceFactory = DeviceFactory(adbSession.deviceServices)

    fun logDevices(devices: DeviceList, label: String) {
      if (LOGGER.isDebugEnabled) {
        LOGGER.debug("trackDevices: $label (${devices.entries.size})")
        devices.entries.forEach {
          LOGGER.debug("trackDevices:   $it")
        }
        LOGGER.debug("trackDevices:   onlineDevicesBySerial = $onlineDevicesBySerial")
        LOGGER.debug("trackDevices:   allDevicesById        = $allDevicesById")
      }
    }

    // Initialize state by reading all current devices
    coroutineScope {
      val devices = adbSession.hostServices.devices()
      logDevices(devices, "Initial deviceList")

      devices.filter { it.isOnline() }.map { async { deviceFactory.createDevice(it.serialNumber) } }.awaitAll().forEach {
        onlineDevicesBySerial[it.serialNumber] = it
        allDevicesById[it.deviceId] = it
        emit(Added(it))
      }
    }

    // Add the preexisting device.
    if (preexistingDevice != null && !allDevicesById.containsKey(preexistingDevice.deviceId)) {
      allDevicesById[preexistingDevice.deviceId] = preexistingDevice
      emit(Added(preexistingDevice))
    }

    try {
      // Track devices changes:
      // We only care about devices that are online.
      // If a previously unknown device comes online, we emit Added
      // If a previously known device comes online, we emit StateChanged
      // If previously online device is missing from the list, we emit a StateChanged.
      adbSession.hostServices.trackDevices().collect { deviceList ->
        logDevices(deviceList, "deviceList")
        val devices = deviceList.entries.filter { it.isOnline() }.associateBy { it.serialNumber }
        devices.values.forEach {
          val serialNumber = it.serialNumber
          if (!onlineDevicesBySerial.containsKey(serialNumber)) {
            val device = deviceFactory.createDevice(it.serialNumber)
            if (allDevicesById.containsKey(device.deviceId)) {
              emit(StateChanged(device))
            }
            else {
              emit(Added(device))
            }
            onlineDevicesBySerial[serialNumber] = device
            allDevicesById[device.deviceId] = device
          }
        }

        // Find devices that were online and are not anymore, then remove them.
        onlineDevicesBySerial.keys.filter { !devices.containsKey(it) }.forEach {
          val device = onlineDevicesBySerial[it] ?: return@forEach
          val deviceOffline = device.copy(isOnline = false)
          emit(StateChanged(deviceOffline))
          onlineDevicesBySerial.remove(it)
          allDevicesById[device.deviceId] = deviceOffline
        }
      }
    } finally {
      LOGGER.debug("trackDevices: Flow ended")
    }
  }
}

private fun DeviceInfo.isOnline(): Boolean = deviceState == ONLINE
