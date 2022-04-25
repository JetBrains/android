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

import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Disconnected
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Online
import com.android.tools.idea.concurrency.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Tests for [ProcessNameMonitor]
 */
@Suppress("EXPERIMENTAL_API_USAGE") // runBlockingTest is experimental
class ProcessNameMonitorImplTest {
  private val projectRule = ProjectRule()

  private val device1 = mockDevice("device1")
  private val device2 = mockDevice("device2")
  private val process1 = ProcessInfo(1, "package1", "process1")
  private val process2 = ProcessInfo(2, "package2", "process2")

  @get:Rule
  val rule = RuleChain(projectRule)

  @Test
  fun devicesOnline() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameMonitor(flows)

      flows.sendDeviceEvents(Online(device1))
      flows.sendDeviceEvents(Online(device2))
      flows.sendClientEvents(device1.serialNumber, clientsAddedEvent(process1))
      flows.sendClientEvents(device2.serialNumber, clientsAddedEvent(process2))

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(device1.serialNumber, 1)).isEqualTo(process1.names)
      assertThat(monitor.getProcessNames(device2.serialNumber, 2)).isEqualTo(process2.names)
      assertThat(monitor.getProcessNames(device1.serialNumber, 2)).isNull()
      assertThat(monitor.getProcessNames(device2.serialNumber, 1)).isNull()
    }
  }

  @Test
  fun deviceDisconnected() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameMonitor(flows)

      flows.sendDeviceEvents(Online(device1))
      flows.sendDeviceEvents(Online(device2))
      flows.sendClientEvents(device1.serialNumber, clientsAddedEvent(process1))
      flows.sendClientEvents(device2.serialNumber, clientsAddedEvent(process2))
      flows.sendDeviceEvents(Disconnected(device1))

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(device1.serialNumber, 1)).isNull()
      assertThat(monitor.getProcessNames(device2.serialNumber, 2)).isEqualTo(process2.names)
      assertThat(monitor.getProcessNames(device1.serialNumber, 2)).isNull()
      assertThat(monitor.getProcessNames(device2.serialNumber, 1)).isNull()
    }
  }

  @Test
  fun disconnect_disposes() {
    val flows = TerminationTrackingProcessNameMonitorFlows()
    ProcessNameMonitorImpl(projectRule.project, flows, EmptyCoroutineContext).apply { start() }

    runBlocking {
      flows.sendDeviceEvents(Online(device1))
      waitForCondition(5, TimeUnit.SECONDS) { flows.isClientFlowStarted(device1.serialNumber) }
      flows.sendDeviceEvents(Disconnected(device1))
    }

    waitForCondition(5, TimeUnit.SECONDS) { flows.isClientFlowTerminated(device1.serialNumber) }
  }

  private fun TestCoroutineScope.processNameMonitor(
    flows: ProcessNameMonitorFlows = FakeProcessNameMonitorFlows()
  ): ProcessNameMonitor {
    return ProcessNameMonitorImpl(projectRule.project, flows, coroutineContext).apply { start() }
  }
}
