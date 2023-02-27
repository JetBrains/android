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
package com.android.tools.idea.adb.processnamemonitor

import com.android.adblib.AdbLogger
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Disconnected
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Online
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking

/**
 * Used to create a [kotlinx.coroutines.flow.Flow] of [DeviceMonitorEvent] from a [IDeviceChangeListener]
 *
 * Only connect/disconnect events are of interest.
 */
internal class DevicesMonitorListener(
  @Suppress("EXPERIMENTAL_API_USAGE") // Not experimental in main
  private val producerScope: ProducerScope<DeviceMonitorEvent>,
  private val logger: AdbLogger,
) : IDeviceChangeListener {
  override fun deviceConnected(device: IDevice) {
    if (device.state == ONLINE) {
      send(Online(device))
    }
  }

  override fun deviceDisconnected(device: IDevice) {
    send(Disconnected(device))
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    if (changeMask and IDevice.CHANGE_STATE != 0 && device.state == ONLINE) {
      send(Online(device))
    }
  }

  private fun send(event: DeviceMonitorEvent) {
    @Suppress("EXPERIMENTAL_API_USAGE") // Not experimental in main
    producerScope.trySendBlocking(event).onFailure {
      logger.warn(it, "Failed to send DeviceMonitorEvent")
    }
  }
}
