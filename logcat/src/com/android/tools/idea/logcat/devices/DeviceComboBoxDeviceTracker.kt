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
import com.android.adblib.serialNumber
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.annotations.VisibleForTesting

/**
 * An implementation of IDeviceComboBoxDeviceTracker that uses an [AdbSession]
 */
internal class DeviceComboBoxDeviceTracker @VisibleForTesting constructor(
  private val deviceProvisioner: DeviceProvisioner,
  private val preexistingDevice: Device?,
) : IDeviceComboBoxDeviceTracker {

  constructor(project: Project, preexistingDevice: Device?) :
    this(project.service<DeviceProvisionerService>().deviceProvisioner, preexistingDevice)

  override suspend fun trackDevices(): Flow<DeviceEvent> {
    val onlineDevicesBySerial = mutableMapOf<String, Device>()
    val allDevicesById = mutableMapOf<String, Device>()

    // Initialize state by reading all current devices
    return flow {
      val initialDevices = deviceProvisioner.devices.value
      initialDevices.filter { it.state.isOnline() }.mapNotNull { it.state.toDevice() }.forEach { device ->
        onlineDevicesBySerial[device.serialNumber] = device
        allDevicesById[device.deviceId] = device
        emit(Added(device))
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
      // If previously online device is missing from the list, we emit a StateChanged.
      deviceProvisioner.devices.statesFlow().collect { states ->
        val onlineStates = states.filter { it.isOnline() }.associateBy { it.connectedDevice?.serialNumber }
        onlineStates.values.forEach { state ->
          val device = state.toDevice() ?: return@forEach
          if (!onlineDevicesBySerial.containsKey(device.serialNumber)) {
            if (allDevicesById.containsKey(device.deviceId)) {
              emit(StateChanged(device))
            }
            else {
              emit(Added(device))
            }
            onlineDevicesBySerial[device.serialNumber] = device
            allDevicesById[device.deviceId] = device
          }
        }

        // Find devices that were online and are not anymore, then remove them.
        onlineDevicesBySerial.keys.filter { !onlineStates.containsKey(it) }.forEach {
          val device = onlineDevicesBySerial[it] ?: return@forEach
          val deviceOffline = device.copy(isOnline = false)
          emit(StateChanged(deviceOffline))
          onlineDevicesBySerial.remove(it)
          allDevicesById[device.deviceId] = deviceOffline
        }
      }
    }.flowOn(Dispatchers.IO)
  }
}

fun Flow<Iterable<DeviceHandle>>.statesFlow(): Flow<List<DeviceState>> =
  @Suppress("OPT_IN_USAGE")
  flatMapLatest { handles ->
    val innerFlows = handles.map(DeviceHandle::stateFlow)
    when {
      innerFlows.isEmpty() -> flowOf(emptyList())
      else -> combine(innerFlows) { states -> states.toList() }
    }
  }
