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

import com.android.ddmlib.IDevice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.Closeable

internal class FakeProcessNameMonitorFlows : ProcessNameMonitorFlows, Closeable {
  private val deviceEventsChannel = Channel<DeviceMonitorEvent>(10)
  private val clientEventsChannels = mutableMapOf<String, Channel<ClientMonitorEvent>>()

  suspend fun sendDeviceEvents(vararg events: DeviceMonitorEvent) {
    events.forEach {
      deviceEventsChannel.send(it)
    }
  }

  suspend fun sendClientEvents(serialNumber: String, vararg events: ClientMonitorEvent) {
    val channel = clientEventsChannels.getOrPut(serialNumber) { Channel(Channel.UNLIMITED) }
    events.forEach { channel.send(it) }
  }

  override fun trackDevices(): Flow<DeviceMonitorEvent> = deviceEventsChannel.consumeAsFlow()

  override fun trackClients(device: IDevice): Flow<ClientMonitorEvent> =
    clientEventsChannels.getOrPut(device.serialNumber) { Channel(Channel.UNLIMITED) }.consumeAsFlow()

  override fun close() {
    deviceEventsChannel.close()
    clientEventsChannels.values.forEach { it.close() }
  }
}
