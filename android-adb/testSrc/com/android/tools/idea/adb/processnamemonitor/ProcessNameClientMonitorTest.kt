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
import com.android.adblib.testing.FakeAdbSession
import com.android.ddmlib.IDevice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [ProcessNameClientMonitor]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ProcessNameClientMonitorTest {
  private val process1 = ProcessInfo(1, "package1", "process1")
  private val process2 = ProcessInfo(2, "package2", "process2")
  private val process3 = ProcessInfo(3, "package3", "process3")
  private val process4 = ProcessInfo(4, "package4", "process4")
  private val process5 = ProcessInfo(5, "package5", "process5")

  private val device = mockDevice("device1")

  private val fakeAdbSession = FakeAdbSession()

  @Test
  fun trackClients_add(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameClientMonitor(device = device, flows = flows)

      flows.sendClientEvents(
        device.serialNumber,
        clientsAddedEvent(process1, process2),
        clientsAddedEvent(process3),
      )

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
      assertThat(monitor.getProcessNames(2)).isEqualTo(process2.names)
      assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
    }
  }

  @Test
  fun trackClients_removeDoesNotEvict(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameClientMonitor(device = device, flows = flows, maxPids = 1000)

      flows.sendClientEvents(
        device.serialNumber,
        clientsAddedEvent(process1, process2, process3),
        clientsRemovedEvent(1, 2, 3),
      )

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
      assertThat(monitor.getProcessNames(2)).isEqualTo(process2.names)
      assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
    }
  }

  @Test
  fun trackClients_overflowRemoveEvicts(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameClientMonitor(device = device, flows = flows, maxPids = 3)

      flows.sendClientEvents(
        device.serialNumber,
        clientsAddedEvent(process1, process2, process3),
        clientMonitorEvent(listOf(process4, process5), listOf(1, 2, 3)),
      )

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(1)).isNull()
      assertThat(monitor.getProcessNames(2)).isNull()
      assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
      assertThat(monitor.getProcessNames(4)).isEqualTo(process4.names)
      assertThat(monitor.getProcessNames(5)).isEqualTo(process5.names)
    }
  }

  @Test
  fun trackClients_reusedPidIsNotEvicted(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameClientMonitor(device = device, flows = flows, maxPids = 3)
      val newProcess1 = ProcessInfo(process1.pid, "newPackage1", "newProcess1")

      flows.sendClientEvents(
        device.serialNumber,
        clientsAddedEvent(process1, process2, process3),
        clientMonitorEvent(listOf(process4, newProcess1), listOf(1, 2, 3)),
      )

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(1)).isEqualTo(newProcess1.names)
      assertThat(monitor.getProcessNames(2)).isNull()
      assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
      assertThat(monitor.getProcessNames(4)).isEqualTo(process4.names)
    }
  }

  @Test
  fun trackClients_reusedPidIsNotEvicted_inLaterEvent(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameClientMonitor(device = device, flows = flows, maxPids = 3)
      val newProcess1 = ProcessInfo(process1.pid, "newPackage1", "newProcess1")

      flows.sendClientEvents(
        device.serialNumber,
        clientsAddedEvent(process1, process2, process3),
        clientsRemovedEvent(1, 2, 3),
        clientsAddedEvent(process4, newProcess1),
      )

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(1)).isEqualTo(newProcess1.names)
      assertThat(monitor.getProcessNames(2)).isNull()
      assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
      assertThat(monitor.getProcessNames(4)).isEqualTo(process4.names)
    }
  }

  @Test
  fun stop_closesFlow(): Unit = runTest {
    val flows = TerminationTrackingProcessNameMonitorFlows()
    val monitor = processNameClientMonitor(device, flows)
    advanceTimeBy(2000) // Let the flow run a few cycles
    assertThat(flows.isClientFlowStarted(device.serialNumber)).isTrue()

    monitor.close()

    advanceUntilIdle()
    assertThat(flows.isClientFlowTerminated(device.serialNumber)).isTrue()
  }


  private fun CoroutineScope.processNameClientMonitor(
    device: IDevice = this@ProcessNameClientMonitorTest.device,
    flows: ProcessNameMonitorFlows = FakeProcessNameMonitorFlows(),
    maxPids: Int = 10
  ): ProcessNameClientMonitor {
    return ProcessNameClientMonitor(this, device, flows, fakeAdbSession, FakeAdbLoggerFactory().logger, maxPidsBeforeEviction = maxPids)
      .apply {
        start()
      }
  }
}
