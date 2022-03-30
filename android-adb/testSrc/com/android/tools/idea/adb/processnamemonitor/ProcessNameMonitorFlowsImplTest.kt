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

import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.ddmlib.IDevice.DeviceState
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.ddmlib.IDevice.DeviceState.ONLINE
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.adb.FakeAdbAdapter
import com.android.tools.idea.concurrency.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import java.io.Closeable
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Tests for [ProcessNameMonitorFlowsImpl]
 */
class ProcessNameMonitorFlowsImplTest {
  @get:Rule
  val rule = RuleChain(ApplicationRule())

  private val adbAdapter = FakeAdbAdapter()

  private val processNameMonitorFlows = ProcessNameMonitorFlowsImpl(adbAdapter)

  private val testScope = TestCoroutineScope(TestCoroutineDispatcher())

  private val eventChannel = Channel<String>(1)

  @Test
  fun trackDevices_noInitialDevices(): Unit = runBlocking {
    testScope.collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      waitForCondition(1, SECONDS) { adbAdapter.deviceChangeListeners.isNotEmpty() }

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
  fun trackDevices_noInitialDevices1(): Unit = runBlocking {
    val job = testScope.async { processNameMonitorFlows.trackDevices().take(3).toList() }

    waitForCondition(1, SECONDS) { adbAdapter.deviceChangeListeners.isNotEmpty() }
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
  fun trackDevices_withInitialDevices(): Unit = runBlocking {
    adbAdapter.devices = listOf(
      mockDevice("device1", OFFLINE),
      mockDevice("device2", ONLINE),
    )

    collectFlowToChannel(processNameMonitorFlows.trackDevices(), eventChannel).use {
      assertThat(eventChannel.receive()).isEqualTo("Online(device=device2)")
    }
  }

  @Test
  fun trackDevices_initialOfflineDevice_becomesOnline(): Unit = runBlocking {
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
  fun trackDevices_jobCanceled_unregisters(): Unit = runBlocking {
    val job = testScope.launch { processNameMonitorFlows.trackDevices().collect { } }
    waitForCondition(1, SECONDS) { adbAdapter.deviceChangeListeners.isNotEmpty() }

    job.cancel()

    // Job.cancel() is asynchronous so rather than assert on the listener being removed, we give it a bit of time. A timeout will fail the
    // test anyway so no need to assert after.
    waitForCondition(1, SECONDS) { adbAdapter.deviceChangeListeners.isEmpty() }
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

private fun mockDevice(serialNumber: String, state: DeviceState = OFFLINE): IDevice {
  return mock<IDevice>().also {
    `when`(it.serialNumber).thenReturn(serialNumber)
    `when`(it.toString()).thenReturn(serialNumber)
    `when`(it.state).thenReturn(state)
    `when`(it.isOnline).thenReturn(state == ONLINE)
    `when`(it.isOffline).thenReturn(state != ONLINE)
  }
}
