/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.selections.deviceprocesses

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProcessListModelTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("ProcessListModelTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var processListModel: ProcessListModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    processListModel = ProcessListModel(myProfilers)
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Test
  fun `online devices with alive processes show in device process list`() {
    assertThat(processListModel.deviceProcessList.value).isEmpty()
    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 20, "FakeProcess1", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 40, "FakeProcess2", Common.Process.State.ALIVE)
    assertThat(processListModel.deviceProcessList.value).isNotEmpty()
    assertThat(processListModel.deviceProcessList.value.size).isEqualTo(2)
  }

  @Test
  fun `offline devices with alive processes do not show in device process list`() {
    assertThat(processListModel.deviceProcessList.value).isEmpty()
    addDeviceWithProcess("FakeDevice1", Common.Device.State.OFFLINE, 20, "FakeProcess1", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.OFFLINE, 40, "FakeProcess2", Common.Process.State.ALIVE)
    assertThat(processListModel.deviceProcessList.value).isEmpty()
  }

  @Test
  fun `online devices with dead processes do not show in device process list`() {
    assertThat(processListModel.deviceProcessList.value).isEmpty()
    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 20, "FakeProcess1", Common.Process.State.DEAD)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 40, "FakeProcess2", Common.Process.State.DEAD)
    assertThat(processListModel.deviceProcessList.value).isEmpty()
  }

  @Test
  fun `test preferred process selection is at the top of device process list`() {
    assertThat(processListModel.deviceProcessList.value).isEmpty()

    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 20, "FakeProcess1", Common.Process.State.ALIVE)
    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myProfilers.setPreferredProcess("FakeProcess1", "FakeProcess1", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess1")

    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 40, "FakeProcess2", Common.Process.State.ALIVE)

    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myProfilers.setPreferredProcess("FakeProcess2", "FakeProcess2", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess2")

    assertThat(processListModel.deviceProcessList.value).isNotEmpty()
    assertThat(processListModel.deviceProcessList.value.size).isEqualTo(2)
    // Make sure that despite being lexicographically greater than the "FakeProcess1", because it is the preferred process, "FakeProcess2"
    // is the first device process listed.
    assertThat(processListModel.deviceProcessList.value.first().process.name).isEqualTo("FakeProcess2")
  }

  @Test
  fun `test preferred process selection and related processes are at top of device process list`() {
    assertThat(processListModel.deviceProcessList.value).isEmpty()

    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 20, "FakeProcess1:X", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 10, "FakeProcess1", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 50, "FakeProcess2:X", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 30, "FakeProcess1:Y", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 60, "FakeProcess2:Y", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 40, "FakeProcess2", Common.Process.State.ALIVE)

    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myProfilers.setPreferredProcess("FakeDevice2", "FakeProcess2:X", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(processListModel.deviceProcessList.value).isNotEmpty()
    assertThat(processListModel.deviceProcessList.value.size).isEqualTo(6)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess2:X")
    var deviceProcessesSorted = processListModel.deviceProcessList.value
    assertThat(deviceProcessesSorted[0].process.name).isEqualTo("FakeProcess2:X")
    assertThat(deviceProcessesSorted[1].process.name).isEqualTo("FakeProcess2")
    assertThat(deviceProcessesSorted[2].process.name).isEqualTo("FakeProcess2:Y")
    assertThat(deviceProcessesSorted[3].process.name).isEqualTo("FakeProcess1")
    assertThat(deviceProcessesSorted[4].process.name).isEqualTo("FakeProcess1:X")
    assertThat(deviceProcessesSorted[5].process.name).isEqualTo("FakeProcess1:Y")

    myProfilers.setPreferredProcess("FakeDevice1", "FakeProcess1", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess1")
    deviceProcessesSorted = processListModel.deviceProcessList.value
    assertThat(deviceProcessesSorted[0].process.name).isEqualTo("FakeProcess1")
    assertThat(deviceProcessesSorted[1].process.name).isEqualTo("FakeProcess1:X")
    assertThat(deviceProcessesSorted[2].process.name).isEqualTo("FakeProcess1:Y")
    assertThat(deviceProcessesSorted[3].process.name).isEqualTo("FakeProcess2")
    assertThat(deviceProcessesSorted[4].process.name).isEqualTo("FakeProcess2:X")
    assertThat(deviceProcessesSorted[5].process.name).isEqualTo("FakeProcess2:Y")
  }

  @Test
  fun `test device process list is lexicographically sorted without setting preferred process`() {
    assertThat(processListModel.deviceProcessList.value).isEmpty()

    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 20, "FakeProcess1:X", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 10, "FakeProcess1", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 50, "FakeProcess2:X", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice1", Common.Device.State.ONLINE, 30, "FakeProcess1:Y", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 60, "FakeProcess2:Y", Common.Process.State.ALIVE)
    addDeviceWithProcess("FakeDevice2", Common.Device.State.ONLINE, 40, "FakeProcess2", Common.Process.State.ALIVE)

    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(processListModel.deviceProcessList.value).isNotEmpty()
    assertThat(processListModel.deviceProcessList.value.size).isEqualTo(6)

    val deviceProcessesSorted = processListModel.deviceProcessList.value
    assertThat(deviceProcessesSorted[0].process.name).isEqualTo("FakeProcess1")
    assertThat(deviceProcessesSorted[1].process.name).isEqualTo("FakeProcess1:X")
    assertThat(deviceProcessesSorted[2].process.name).isEqualTo("FakeProcess1:Y")
    assertThat(deviceProcessesSorted[3].process.name).isEqualTo("FakeProcess2")
    assertThat(deviceProcessesSorted[4].process.name).isEqualTo("FakeProcess2:X")
    assertThat(deviceProcessesSorted[5].process.name).isEqualTo("FakeProcess2:Y")
  }

  private fun addDeviceWithProcess(deviceName: String,
                                   deviceState: Common.Device.State,
                                   processId: Int,
                                   processName: String,
                                   processState: Common.Process.State) {

    val device = createDevice(deviceName, deviceState)
    val process = createProcess(processId, processName, processState, device.deviceId)
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  private fun createDevice(deviceName: String, deviceState: Common.Device.State) = Common.Device.newBuilder().setDeviceId(
    deviceName.hashCode().toLong()).setSerial(deviceName).setState(deviceState).build()

  private fun createProcess(pid: Int,
                            processName: String,
                            processState: Common.Process.State,
                            deviceId: Long) = Common.Process.newBuilder().setDeviceId(deviceId).setPid(pid).setName(processName).setState(
    processState).setExposureLevel(ExposureLevel.DEBUGGABLE).build()
}