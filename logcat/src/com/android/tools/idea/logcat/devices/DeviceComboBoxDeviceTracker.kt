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
import com.android.adblib.DevicePropertyNames.RO_BOOT_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_RELEASE
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DevicePropertyNames.RO_KERNEL_QEMU_AVD_NAME
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MANUFACTURER
import com.android.adblib.DevicePropertyNames.RO_PRODUCT_MODEL
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.shellAsText
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.CoroutineContext

private val ADB_TIMEOUT = Duration.ofMillis(1000)

/**
 * An implementation of IDeviceComboBoxDeviceTracker that uses an [AdbSession]
 */
internal class DeviceComboBoxDeviceTracker(
  project: Project,
  private val preexistingDevice: Device?,
  private val adbSession: AdbSession = AdbLibService.getSession(project),
  private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : IDeviceComboBoxDeviceTracker {

  override suspend fun trackDevices(): Flow<DeviceEvent> {
    return flow {
      while (true) {
        // TODO(b/228224334): This should be handled internally by AdbLib
        try {
          trackDevicesInternal()
        }
        catch (e: IOException) {
          LOGGER.info("Device tracker exception, restarting it...", e)
          emit(TrackingReset(e))
          continue
        }
        break
      }
    }.flowOn(coroutineContext)
  }

  private suspend fun FlowCollector<DeviceEvent>.trackDevicesInternal() {
    val onlineDevicesBySerial = mutableMapOf<String, Device>()
    val allDevicesById = mutableMapOf<String, Device>()

    // Initialize state by reading all current devices
    coroutineScope {
      val devices = adbSession.hostServices.devices()
      devices.filter { it.isOnline() }.map { async { it.toDevice() } }.awaitAll().forEach {
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

    // Track devices changes:
    // We only care about devices that are online.
    // If a previously unknown device comes online, we emit Added
    // If a previously known device comes online, we emit StateChanged
    // If previously online device is missing from the kist, we emit a StateChanged.
    adbSession.hostServices.trackDevices().collect { deviceList ->
      val devices = deviceList.entries.filter { it.isOnline() }.associateBy { it.serialNumber }
      devices.values.forEach {
        val serialNumber = it.serialNumber
        if (!onlineDevicesBySerial.containsKey(serialNumber)) {
          val device = it.toDevice()
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
  }

  private suspend fun DeviceInfo.toDevice(): Device {
    if (serialNumber.startsWith("emulator-")) {
      val properties = getProperties(RO_BUILD_VERSION_RELEASE, RO_BUILD_VERSION_SDK, RO_BOOT_QEMU_AVD_NAME, RO_KERNEL_QEMU_AVD_NAME)
      return Device.createEmulator(
        serialNumber,
        isOnline = true,
        properties.getValue(RO_BUILD_VERSION_RELEASE).toIntOrNull() ?: 0,
        properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0,
        getAvdName(properties))
    }
    else {
      val properties = getProperties(RO_BUILD_VERSION_RELEASE, RO_BUILD_VERSION_SDK, RO_PRODUCT_MANUFACTURER, RO_PRODUCT_MODEL)
      return Device.createPhysical(
        serialNumber,
        isOnline = true,
        properties.getValue(RO_BUILD_VERSION_RELEASE).toIntOrNull() ?: 0,
        properties.getValue(RO_BUILD_VERSION_SDK).toIntOrNull() ?: 0,
        properties.getValue(RO_PRODUCT_MANUFACTURER),
        properties.getValue(RO_PRODUCT_MODEL))
    }
  }

  private fun DeviceInfo.getAvdName(properties: Map<String, String>): String =
    properties.getValue(RO_BOOT_QEMU_AVD_NAME).ifBlank { properties.getValue(RO_KERNEL_QEMU_AVD_NAME) }.ifBlank {
      LOGGER.warn("Emulator has no avd_name property")
      serialNumber
    }

  @Suppress("SameParameterValue") // The inspection is wrong. It only considers the first arg in the vararg
  private suspend fun DeviceInfo.getProperties(vararg properties: String): Map<String, String> {
    val selector = DeviceSelector.fromSerialNumber(serialNumber)
    val command = properties.joinToString(" ; ") { "getprop $it" }
    val lines = adbSession.deviceServices.shellAsText(selector, command, commandTimeout = ADB_TIMEOUT).split("\n")
    return properties.withIndex().associate { it.value to lines[it.index].trimEnd('\r') }
  }
}

private fun DeviceInfo.isOnline(): Boolean = deviceState == ONLINE
