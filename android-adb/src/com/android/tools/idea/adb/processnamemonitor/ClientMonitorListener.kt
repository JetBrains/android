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
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.Client.CHANGE_NAME
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent.ClientChanged
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent.ClientListChanged
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking

/**
 * Used to create a [kotlinx.coroutines.flow.Flow] of [ClientEvent] from a [IDeviceChangeListener]/[IClientChangeListener].
 *
 * Events are only monitored for the specified device.
 */
internal class ClientMonitorListener(
  private val device: IDevice,
  @Suppress("EXPERIMENTAL_API_USAGE") // Not experimental in main
  private val flow: ProducerScope<ClientEvent>,
  private val logger: AdbLogger,
) : IDeviceChangeListener, IClientChangeListener {

  override fun deviceConnected(device: IDevice) {}

  override fun deviceDisconnected(device: IDevice) {}

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    if (this.device == device && changeMask and CHANGE_CLIENT_LIST != 0) {
      send(ClientListChanged(device.clients))
    }
  }

  override fun clientChanged(client: Client, changeMask: Int) {
    if (changeMask and CHANGE_NAME != 0 && client.device == device) {
      send(ClientChanged(client))
    }
  }

  private fun send(event: ClientEvent) {
    @Suppress("EXPERIMENTAL_API_USAGE") // Not experimental in main
    flow.trySendBlocking(event).onFailure {
      logger.warn(it, "Failed to send ClientEvent")
    }
  }

  /**
   * An event sent when the state of the clients on a device changes.
   */
  internal sealed class ClientEvent {
    /**
     * Sent when clients are added/removed
     */
    class ClientListChanged(val clients: Array<out Client>) : ClientEvent() {
      override fun toString(): String {
        return clients.joinToString(prefix = "ClientListChanged: ") {
          "${it.clientData.pid}: ${it.clientData.packageName} ${it.clientData.clientDescription}"
        }
      }
    }

    /**
     * Sent when a client changes
     */
    class ClientChanged(val client: Client) : ClientEvent() {
      override fun toString(): String {
        return "ClientChanged: ${client.clientData.pid}: ${client.clientData.packageName} ${client.clientData.clientDescription}"
      }
    }
  }
}
