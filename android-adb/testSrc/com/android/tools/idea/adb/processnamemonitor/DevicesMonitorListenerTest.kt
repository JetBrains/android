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

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.IDevice.CHANGE_BUILD_INFO
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IDevice.DeviceState.FASTBOOTD
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.ddmlib.IDevice.DeviceState.UNAUTHORIZED
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Disconnected
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Online
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for [DevicesMonitorListener]
 */
@Suppress("EXPERIMENTAL_API_USAGE")
class DevicesMonitorListenerTest {
  private val logger = FakeAdbLoggerFactory().logger

  @Test
  fun deviceConnected() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = DevicesMonitorListener(this, logger)

        monitor.deviceConnected(mockDevice("device1", ONLINE))
        monitor.deviceConnected(mockDevice("device2", ONLINE))
        monitor.deviceConnected(mockDevice("device3", FASTBOOTD))
        monitor.deviceConnected(mockDevice("device3", OFFLINE))
        monitor.deviceConnected(mockDevice("device4", DISCONNECTED))

        close()
        awaitClose { }
      }

      assertThat(flow.toEventList()).containsExactly(
        "device1 connected",
        "device2 connected",
      ).inOrder()
    }
  }

  @Test
  fun deviceDisconnected() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = DevicesMonitorListener(this, logger)

        monitor.deviceDisconnected(mockDevice("device1", ONLINE))
        monitor.deviceDisconnected(mockDevice("device2", FASTBOOTD))
        monitor.deviceDisconnected(mockDevice("device3", OFFLINE))
        monitor.deviceDisconnected(mockDevice("device4", DISCONNECTED))

        close()
        awaitClose { }
      }

      assertThat(flow.toEventList()).containsExactly(
        "device1 disconnected",
        "device2 disconnected",
        "device3 disconnected",
        "device4 disconnected",
      ).inOrder()
    }
  }

  @Test
  fun deviceChanged() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = DevicesMonitorListener(this, logger)

        monitor.deviceChanged(mockDevice("device1", ONLINE), CHANGE_STATE)
        monitor.deviceChanged(mockDevice("device2", ONLINE), CHANGE_STATE)
        monitor.deviceChanged(mockDevice("device3", ONLINE), CHANGE_CLIENT_LIST)
        monitor.deviceChanged(mockDevice("device4", ONLINE), CHANGE_BUILD_INFO)
        monitor.deviceChanged(mockDevice("device5", FASTBOOTD), CHANGE_STATE)
        monitor.deviceChanged(mockDevice("device6", OFFLINE), CHANGE_STATE)
        monitor.deviceChanged(mockDevice("device7", DISCONNECTED), CHANGE_STATE)

        close()
        awaitClose { }
      }

      assertThat(flow.toEventList()).containsExactly(
        "device1 connected",
        "device2 connected",
      ).inOrder()
    }
  }

  @Test
  fun realisticLyfecycle() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = DevicesMonitorListener(this, logger)

        val device = mockDevice("device1", OFFLINE)

        monitor.deviceConnected(device.setState(OFFLINE))
        monitor.deviceChanged(device.setState(UNAUTHORIZED), CHANGE_STATE)
        monitor.deviceChanged(device.setState(ONLINE), CHANGE_STATE)
        monitor.deviceChanged(device.setState(OFFLINE), CHANGE_STATE)
        monitor.deviceDisconnected(device.setState(DISCONNECTED))
        monitor.deviceConnected(device.setState(OFFLINE))
        monitor.deviceChanged(device.setState(UNAUTHORIZED), CHANGE_STATE)
        monitor.deviceChanged(device.setState(ONLINE), CHANGE_STATE)

        close()
        awaitClose { }
      }

      assertThat(flow.toEventList()).containsExactly(
        "device1 connected",
        "device1 disconnected",
        "device1 connected",
      ).inOrder()
    }
  }

}

private suspend fun Flow<DeviceMonitorEvent>.toEventList(): List<String> {
  return toList().map {
    when (it) {
      is Online -> "${it.device.serialNumber} connected"
      is Disconnected -> "${it.device.serialNumber} disconnected"
    }
  }
}
