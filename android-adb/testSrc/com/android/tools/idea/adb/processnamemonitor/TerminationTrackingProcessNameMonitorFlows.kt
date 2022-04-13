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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import java.io.Closeable

/**
 * A fake implementation of ProcessNameMonitorFlows that allows caller to check if a client flow has terminated. Used to test that
 * dispose() is closing the scope.
 */
internal class TerminationTrackingProcessNameMonitorFlows : ProcessNameMonitorFlows, Closeable {
  private val deviceEventsChannel = Channel<DeviceMonitorEvent>(10)
  private val clientFlowStarted = mutableMapOf<String, Boolean>()
  private val clientFlowTerminated = mutableMapOf<String, Boolean>()

  suspend fun sendDeviceEvents(vararg events: DeviceMonitorEvent) {
    events.forEach {
      deviceEventsChannel.send(it)
    }
  }

  fun isClientFlowStarted(serialNumber: String) = clientFlowStarted.getOrDefault(serialNumber, false)
  fun isClientFlowTerminated(serialNumber: String) = clientFlowTerminated.getOrDefault(serialNumber, false)

  override fun trackDevices(): Flow<DeviceMonitorEvent> = deviceEventsChannel.consumeAsFlow()

  override fun close() {
    deviceEventsChannel.close()
  }

  override fun trackClients(device: IDevice): Flow<ClientMonitorEvent> = flow {
    try {
      while (true) {
        clientFlowStarted[device.serialNumber] = true
        delay(1000)
      }
    }
    finally {
      clientFlowTerminated[device.serialNumber] = true
    }
  }
}
