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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.android.tools.idea.adb.processnamemonitor

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.Client.CHANGE_NAME
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.tools.idea.adb.FakeAdbAdapter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.io.Closeable

/**
 * Tests for [ProcessNameMonitorFlowsImpl]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ProcessNameMonitorFlowsImplTest {
  private val adbAdapter = FakeAdbAdapter()

  private val processNameMonitorFlows = ProcessNameMonitorFlowsImpl(adbAdapter, FakeAdbLoggerFactory().logger)

  private val eventChannel = Channel<String>(1)

  private val client1 = mockClient(1, "package1", "process1")
  private val client2 = mockClient(2, "package2", "process2")
  private val client3 = mockClient(3, "package3", "process3")

  @Test
  fun trackDevices_noInitialDevices(): Unit = runTest {
    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      advanceUntilIdle()

      adbAdapter.fireDeviceConnected(mockDevice("device1", ONLINE))
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device1)")

      adbAdapter.fireDeviceDisconnected(mockDevice("device1", DISCONNECTED))
      assertThat(eventChannel.receive()).isEqualTo("Disconnected(device=device1)")

      adbAdapter.fireDeviceConnected(mockDevice("device1", OFFLINE))
      assertThat(withTimeoutOrNull(1000) { eventChannel.receive() }).named("Expected to timeout").isEqualTo(null)

      adbAdapter.fireDeviceChange(mockDevice("device1", ONLINE), CHANGE_STATE)
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device1)")
    }
  }

  @Test
  fun trackDevices_noInitialDevices1(): Unit = runTest {
    val job = async { processNameMonitorFlows.trackDevices().take(3).toList() }
    advanceUntilIdle()

    adbAdapter.fireDeviceConnected(mockDevice("device1", ONLINE))
    adbAdapter.fireDeviceDisconnected(mockDevice("device1", ONLINE))
    adbAdapter.fireDeviceConnected(mockDevice("device1", OFFLINE))
    adbAdapter.fireDeviceChange(mockDevice("device1", ONLINE), CHANGE_STATE)

    assertThat(job.await().map { it.toString() }).containsExactly(
      "Online(device=device1)",
      "Disconnected(device=device1)",
      "Online(device=device1)",
    ).inOrder()
  }

  @Test
  fun trackDevices_withInitialDevices(): Unit = runTest {
    adbAdapter.devices = listOf(
      mockDevice("device1", OFFLINE),
      mockDevice("device2", ONLINE),
    )

    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device2)")
    }
  }

  @Test
  fun trackDevices_initialOfflineDevice_becomesOnline(): Unit = runTest {
    adbAdapter.devices = listOf(
      mockDevice("device1", OFFLINE),
      mockDevice("device2", ONLINE),
    )

    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device2)")

      adbAdapter.fireDeviceConnected(mockDevice("device1", ONLINE))
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device1)")
    }
  }

  @Test
  fun trackDevices_jobCanceled_unregisters(): Unit = runTest {
    val job = launch { processNameMonitorFlows.trackDevices().collect { } }
    advanceUntilIdle()
    assertThat(adbAdapter.deviceChangeListeners).isNotEmpty()

    job.cancel()

    advanceUntilIdle()
    assertThat(adbAdapter.deviceChangeListeners).isEmpty()
  }

  @Test
  fun trackClients_initialClients(): Unit = runTest {
    val device = mockDevice("device", ONLINE).withClients(client1, client2)

    collectFlowToChannel(processNameMonitorFlows.trackClients(device), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Added: [1->(package1/process1), 2->(package2/process2)] Removed: []")
    }
  }

  @Test
  fun trackClients_clientListChanged(): Unit = runTest {
    val device = mockDevice("device", ONLINE)

    collectFlowToChannel(processNameMonitorFlows.trackClients(device.withClients(client1)), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Added: [1->(package1/process1)] Removed: []")

      adbAdapter.fireDeviceChange(device.withClients(client2, client3), CHANGE_CLIENT_LIST)
      assertThat(eventChannel.receive()).isEqualTo("Added: [2->(package2/process2), 3->(package3/process3)] Removed: [1]")

      adbAdapter.fireDeviceChange(device.withClients(client3), CHANGE_CLIENT_LIST)
      assertThat(eventChannel.receive()).isEqualTo("Added: [] Removed: [2]")
    }
  }

  @Test
  fun trackClients_clientListChanged_otherDevice(): Unit = runTest {
    val device1 = mockDevice("device1", ONLINE)
    val device2 = mockDevice("device2", ONLINE)

    collectFlowToChannel(processNameMonitorFlows.trackClients(device1.withClients(client1)), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Added: [1->(package1/process1)] Removed: []")

      adbAdapter.fireDeviceChange(device2.withClients(client2, client3), CHANGE_CLIENT_LIST)
      assertThat(withTimeoutOrNull(1000) { eventChannel.receive() }).named("Expected to time out").isNull()
    }
  }

  @Test
  fun trackClients_clientWithoutName(): Unit = runTest {
    val client4 = mockClient(4, packageName = null, processName = null)
    val device = mockDevice("device", ONLINE).withClients(client1, client4)

    collectFlowToChannel(processNameMonitorFlows.trackClients(device), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Added: [1->(package1/process1)] Removed: []")

      adbAdapter.fireClientChange(client4.withNames("package4", "process4"), CHANGE_NAME)
      assertThat(eventChannel.receive()).isEqualTo("Added: [4->(package4/process4)] Removed: []")
    }
  }

  @Test
  fun trackClients_clientWithoutName_otherDevice(): Unit = runTest {
    val client4Device1 = mockClient(4, packageName = null, processName = null)
    val device1 = mockDevice("device1", ONLINE).withClients(client1, client4Device1)
    val client4Device2 = mockClient(4, packageName = null, processName = null)
    mockDevice("device2", ONLINE).withClients(client1, client4Device2)

    collectFlowToChannel(processNameMonitorFlows.trackClients(device1), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Added: [1->(package1/process1)] Removed: []")

      adbAdapter.fireClientChange(client4Device2.withNames("package4", "process4"), CHANGE_NAME)
      assertThat(withTimeoutOrNull(1000) { eventChannel.receive() }).named("Expected to time out").isNull()
    }
  }

  /**
   * Collect a flow and sent the results into a channel (converting to String for easy debugging). Return the Job wrapped by a Closeable,
   * so we don't forget to cancel it.
   */
  private fun <T> CoroutineScope.collectFlowToChannel(flow: Flow<T>, channel: Channel<String>): Closeable {
    return object : Closeable {
      private val job = launch { flow.collect { channel.send(it.toString()) } }
      override fun close() {
        job.cancel()
      }
    }
  }
}
