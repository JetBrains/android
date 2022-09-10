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

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.use
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit.SECONDS

private const val PS_COMMAND = "ps -A -o PID,NAME"

/**
 * Tests for [ProcessNameClientMonitor]
 */
@Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
class ProcessNameClientMonitorTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule)

  private val process1 = ProcessInfo(1, "package1", "process1")
  private val process2 = ProcessInfo(2, "package2", "process2")
  private val process3 = ProcessInfo(3, "package3", "process3")
  private val process4 = ProcessInfo(4, "package4", "process4")
  private val process5 = ProcessInfo(5, "package5", "process5")

  private val device = mockDevice("device1")

  private val fakeAdbDeviceServices = FakeAdbSession().deviceServices

  @Before
  fun setUp() {
    fakeAdbDeviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(device.serialNumber), PS_COMMAND, "")
    if (StudioFlags.ADBLIB_LEGACY_SHELL_FOR_PS_MONITOR.get()) {
      fakeAdbDeviceServices.configureShellCommand(DeviceSelector.fromSerialNumber(device.serialNumber), "getprop", "")
    }
  }

  @Test
  fun trackClients_add() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(device = device, flows = flows).use { monitor ->

        flows.sendClientEvents(
          device.serialNumber,
          clientsAddedEvent(process1, process2),
          clientsAddedEvent(process3),
        )

        assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
        assertThat(monitor.getProcessNames(2)).isEqualTo(process2.names)
        assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)

      }
    }
  }

  @Test
  fun trackClients_removeDoesNotEvict() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(device = device, flows = flows, maxPids = 1000).use { monitor ->

        flows.sendClientEvents(
          device.serialNumber,
          clientsAddedEvent(process1, process2, process3),
          clientsRemovedEvent(1, 2, 3),
        )

        assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
        assertThat(monitor.getProcessNames(2)).isEqualTo(process2.names)
        assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)

      }
    }
  }

  @Test
  fun trackClients_overflowRemoveEvicts() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(device = device, flows = flows, maxPids = 3).use { monitor ->

        flows.sendClientEvents(
          device.serialNumber,
          clientsAddedEvent(process1, process2, process3),
          clientMonitorEvent(listOf(process4, process5), listOf(1, 2, 3)),
        )

        assertThat(monitor.getProcessNames(1)).isNull()
        assertThat(monitor.getProcessNames(2)).isNull()
        assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
        assertThat(monitor.getProcessNames(4)).isEqualTo(process4.names)
        assertThat(monitor.getProcessNames(5)).isEqualTo(process5.names)

      }
    }
  }

  @Test
  fun trackClients_reusedPidIsNotEvicted() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(device = device, flows = flows, maxPids = 3).use { monitor ->

        val newProcess1 = ProcessInfo(process1.pid, "newPackage1", "newProcess1")
        flows.sendClientEvents(
          device.serialNumber,
          clientsAddedEvent(process1, process2, process3),
          clientMonitorEvent(listOf(process4, newProcess1), listOf(1, 2, 3)),
        )

        assertThat(monitor.getProcessNames(1)).isEqualTo(newProcess1.names)
        assertThat(monitor.getProcessNames(2)).isNull()
        assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
        assertThat(monitor.getProcessNames(4)).isEqualTo(process4.names)

      }
    }
  }

  @Test
  fun trackClients_reusedPidIsNotEvicted_inLaterEvent() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(device = device, flows = flows, maxPids = 3).use { monitor ->

        val newProcess1 = ProcessInfo(process1.pid, "newPackage1", "newProcess1")
        flows.sendClientEvents(
          device.serialNumber,
          clientsAddedEvent(process1, process2, process3),
          clientsRemovedEvent(1, 2, 3),
          clientsAddedEvent(process4, newProcess1),
        )

        assertThat(monitor.getProcessNames(1)).isEqualTo(newProcess1.names)
        assertThat(monitor.getProcessNames(2)).isNull()
        assertThat(monitor.getProcessNames(3)).isEqualTo(process3.names)
        assertThat(monitor.getProcessNames(4)).isEqualTo(process4.names)

      }
    }
  }

  @Test
  fun dispose_closesFlow() = runBlockingTest {
    val flows = TerminationTrackingProcessNameMonitorFlows()
    processNameClientMonitor(device, flows).use {
      waitForCondition(5, SECONDS) { flows.isClientFlowStarted(device.serialNumber) }
    }

    waitForCondition(5, SECONDS) { flows.isClientFlowTerminated(device.serialNumber) }
  }

  @Test
  fun processNamesFromPs() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(flows = flows, device = device).use { monitor ->
        fakeAdbDeviceServices.configureShellCommand(
          DeviceSelector.fromSerialNumber(device.serialNumber),
          PS_COMMAND,
          """
          2 process2-from-ps
          3 process3-from-ps

        """.trimIndent())

        flows.sendClientEvents(device.serialNumber, clientsAddedEvent(process1))

        advanceTimeBy(2000)
        assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
        assertThat(monitor.getProcessNames(2)).isEqualTo(ProcessNames("", "process2-from-ps"))
        assertThat(monitor.getProcessNames(3)).isEqualTo(ProcessNames("", "process3-from-ps"))
      }
    }
  }

  @Test
  fun processNamesFromPs_preferFromClient() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(flows = flows, device = device).use { monitor ->
        fakeAdbDeviceServices.configureShellCommand(
          DeviceSelector.fromSerialNumber(device.serialNumber),
          PS_COMMAND,
          """
          1 process1-from-ps

        """.trimIndent())

        flows.sendClientEvents(device.serialNumber, clientsAddedEvent(process1))

        advanceTimeBy(2000)
        assertThat(monitor.getProcessNames(1)).isEqualTo(process1.names)
      }
    }
  }

  @Test
  fun processNamesFromPs_multipleRuns() = runBlockingTest {
    FakeProcessNameMonitorFlows().use { flows ->
      processNameClientMonitor(flows = flows, device = device).use { monitor ->

        fakeAdbDeviceServices.configureShellCommand(
          DeviceSelector.fromSerialNumber(device.serialNumber),
          PS_COMMAND,
          """
          2 process2-from-ps
          3 process3-from-ps

        """.trimIndent())
        advanceTimeBy(2000)

        fakeAdbDeviceServices.configureShellCommand(
          DeviceSelector.fromSerialNumber(device.serialNumber),
          PS_COMMAND,
          """
          3 new-process3-from-ps
          4 process4-from-ps

        """.trimIndent())
        advanceTimeBy(2000)

        assertThat(monitor.getProcessNames(2)).isNull()
        assertThat(monitor.getProcessNames(3)).isEqualTo(ProcessNames("", "new-process3-from-ps"))
        assertThat(monitor.getProcessNames(4)).isEqualTo(ProcessNames("", "process4-from-ps"))
      }
    }
  }

  private fun TestCoroutineScope.processNameClientMonitor(
    device: IDevice = this@ProcessNameClientMonitorTest.device,
    flows: ProcessNameMonitorFlows = FakeProcessNameMonitorFlows(),
    maxPids: Int = 10
  ): ProcessNameClientMonitor {
    return ProcessNameClientMonitor(
      projectRule.project,
      this,
      device,
      flows,
      { fakeAdbDeviceServices },
      maxPids)
      .apply { start() }
  }
}
