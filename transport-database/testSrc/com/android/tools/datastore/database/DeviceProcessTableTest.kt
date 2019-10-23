/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.datastore.database

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.AgentStatusRequest
import com.android.tools.profiler.proto.Transport.GetDevicesResponse
import com.android.tools.profiler.proto.Transport.GetProcessesRequest
import com.android.tools.profiler.proto.Transport.GetProcessesResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.function.Consumer

class DeviceProcessTableTest : DatabaseTest<DeviceProcessTable>() {
  companion object {
    val FAKE_DEVICE_ID = 1L
  }

  override fun createTable(): DeviceProcessTable {
    return DeviceProcessTable()
  }

  override fun getTableQueryMethodsForVerification(): List<Consumer<DeviceProcessTable>> {
    return mutableListOf(
      (Consumer { assertThat(it.getDevices()).isEqualTo(GetDevicesResponse.getDefaultInstance()) }),
      (Consumer {
        assertThat(it.getProcesses(GetProcessesRequest.getDefaultInstance())).isEqualTo(GetProcessesResponse.getDefaultInstance())
      }),
      (Consumer {
        assertThat(it.getAgentStatus(AgentStatusRequest.getDefaultInstance())).isEqualTo(Common.AgentData.getDefaultInstance())
      }),
      (Consumer { it.insertOrUpdateDevice(Common.Device.getDefaultInstance()) }),
      (Consumer { it.insertOrUpdateProcess(-1, Common.Process.getDefaultInstance()) }),
      (Consumer {
        it.updateAgentStatus(-1, Common.Process.getDefaultInstance(), Common.AgentData.getDefaultInstance())
      }))
  }

  @Test
  fun testExistingProcessIsUpdated() {
    val process = Common.Process.newBuilder().setDeviceId(FAKE_DEVICE_ID).setPid(99).setName("FakeProcess").setState(
      Common.Process.State.ALIVE)
      .setStartTimestampNs(10).build()

    // Setup initial process and status.
    val status = Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build()
    table.insertOrUpdateProcess(FAKE_DEVICE_ID, process)
    table.updateAgentStatus(FAKE_DEVICE_ID, process, status)

    // Double-check the process has been added.
    var processes = table.getProcesses(GetProcessesRequest.newBuilder().setDeviceId(FAKE_DEVICE_ID).build())
    assertThat(processes.processList).hasSize(1)
    assertThat(processes.getProcess(0)).isEqualTo(process)

    // Double-check status has been set.
    val request = AgentStatusRequest.newBuilder().setPid(process.pid).setDeviceId(FAKE_DEVICE_ID).build()
    assertThat(table.getAgentStatus(request).status).isEqualTo(Common.AgentData.Status.ATTACHED)

    // Kill the process entry and verify that the process state is updated and the agent status remains the same.
    val deadProcess = process.toBuilder().setState(Common.Process.State.DEAD).build()
    table.insertOrUpdateProcess(FAKE_DEVICE_ID, deadProcess)
    processes = table.getProcesses(GetProcessesRequest.newBuilder().setDeviceId(FAKE_DEVICE_ID).build())
    assertThat(processes.processList).hasSize(1)
    assertThat(processes.getProcess(0)).isEqualTo(deadProcess)
    assertThat(table.getAgentStatus(request).status).isEqualTo(Common.AgentData.Status.ATTACHED)

    // Resurrects the process and verify that the start time does not change. This is a scenario for Emulator Snapshots.
    val resurrectedProcess = process.toBuilder().setStartTimestampNs(20).build()
    table.insertOrUpdateProcess(FAKE_DEVICE_ID, resurrectedProcess)
    processes = table.getProcesses(GetProcessesRequest.newBuilder().setDeviceId(FAKE_DEVICE_ID).build())
    assertThat(processes.processList).hasSize(1)
    assertThat(processes.getProcess(0)).isEqualTo(process)
    assertThat(table.getAgentStatus(request).status).isEqualTo(Common.AgentData.Status.ATTACHED)
  }
}