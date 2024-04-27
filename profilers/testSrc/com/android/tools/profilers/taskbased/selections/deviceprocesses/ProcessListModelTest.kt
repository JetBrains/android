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
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.addDeviceWithProcess
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createDevice
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createProcess
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
    processListModel = ProcessListModel(myProfilers) {}
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Test
  fun `online device with alive processes show in device process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()
    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    processListModel.onDeviceSelection(device)
    addDeviceWithProcess(device, createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(40, "FakeProcess2", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(2)
  }

  @Test
  fun `offline device with alive processes do not show in device process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()
    val device = createDevice("FakeDevice", Common.Device.State.OFFLINE)
    processListModel.onDeviceSelection(device)
    addDeviceWithProcess(device, createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(40, "FakeProcess2", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    assertThat(processListModel.getSelectedDeviceProcesses()).isEmpty()
  }

  @Test
  fun `online device with dead processes do not show in device process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()
    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    processListModel.onDeviceSelection(device)
    addDeviceWithProcess(device, createProcess(20, "FakeProcess1", Common.Process.State.DEAD, device.deviceId), myTransportService, myTimer)
    addDeviceWithProcess(device, createProcess(40, "FakeProcess2", Common.Process.State.DEAD, device.deviceId), myTransportService, myTimer)
    assertThat(processListModel.getSelectedDeviceProcesses()).isEmpty()
  }

  @Test
  fun `changing selected device changes process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()
    val device1 = createDevice("FakeDevice1", Common.Device.State.ONLINE)
    val device2 = createDevice("FakeDevice2", Common.Device.State.ONLINE)
    addDeviceWithProcess(device1, createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, device1.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device2, createProcess(40, "FakeProcess2", Common.Process.State.ALIVE, device2.deviceId), myTransportService,
                         myTimer)

    // Make sure there are two entries in the device to process list mapping.
    assertThat(processListModel.deviceToProcesses.value.size).isEqualTo(2)

    // Select device1 and make sure that the selected device processes are correct.
    processListModel.onDeviceSelection(device1)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().first().name).isEqualTo("FakeProcess1")

    // Select device2 and make sure that the selected device processes are correct.
    processListModel.onDeviceSelection(device2)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().first().name).isEqualTo("FakeProcess2")
  }

  @Test
  fun `test preferred process selection is at the top of device process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()

    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    processListModel.onDeviceSelection(device)

    val process1 = createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId)
    addDeviceWithProcess(device, process1, myTransportService, myTimer)
    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess1", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess1")

    val process2 = createProcess(40, "FakeProcess2", Common.Process.State.ALIVE, device.deviceId)
    addDeviceWithProcess(device, process2, myTransportService, myTimer)

    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess2", null)
    myProfilers.deviceProcessMap.keys.find { it == device }
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess2")

    assertThat(processListModel.deviceToProcesses.value).isNotEmpty()
    assertThat(processListModel.deviceToProcesses.value.size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(2)
    // Make sure that despite being lexicographically greater than the "FakeProcess1", because it is the preferred process, "FakeProcess2"
    // is the first device process listed.
    assertThat(processListModel.getSelectedDeviceProcesses().first().name).isEqualTo("FakeProcess2")
  }

  @Test
  fun `test preferred process selection and related processes are at top of device process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()

    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    processListModel.onDeviceSelection(device)

    addDeviceWithProcess(device, createProcess(10, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(20, "FakeProcess1:X", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(30, "FakeProcess1:Y", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(40, "FakeProcess2", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(50, "FakeProcess2:X", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(60, "FakeProcess2:Y", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)

    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess2:X", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(processListModel.deviceToProcesses.value).isNotEmpty()
    assertThat(processListModel.deviceToProcesses.value.size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(6)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess2:X")
    var deviceProcessesSorted = processListModel.getSelectedDeviceProcesses()
    assertThat(deviceProcessesSorted[0].name).isEqualTo("FakeProcess2:X")
    assertThat(deviceProcessesSorted[1].name).isEqualTo("FakeProcess2")
    assertThat(deviceProcessesSorted[2].name).isEqualTo("FakeProcess2:Y")
    assertThat(deviceProcessesSorted[3].name).isEqualTo("FakeProcess1")
    assertThat(deviceProcessesSorted[4].name).isEqualTo("FakeProcess1:X")
    assertThat(deviceProcessesSorted[5].name).isEqualTo("FakeProcess1:Y")

    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess1", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(processListModel.deviceToProcesses.value).isNotEmpty()
    assertThat(processListModel.deviceToProcesses.value.size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(6)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess1")
    deviceProcessesSorted = processListModel.getSelectedDeviceProcesses()
    assertThat(deviceProcessesSorted[0].name).isEqualTo("FakeProcess1")
    assertThat(deviceProcessesSorted[1].name).isEqualTo("FakeProcess1:X")
    assertThat(deviceProcessesSorted[2].name).isEqualTo("FakeProcess1:Y")
    assertThat(deviceProcessesSorted[3].name).isEqualTo("FakeProcess2")
    assertThat(deviceProcessesSorted[4].name).isEqualTo("FakeProcess2:X")
    assertThat(deviceProcessesSorted[5].name).isEqualTo("FakeProcess2:Y")
  }


  @Test
  fun `test device process list is lexicographically sorted without setting preferred process`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()

    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    processListModel.onDeviceSelection(device)

    addDeviceWithProcess(device, createProcess(10, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(20, "FakeProcess1:X", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(30, "FakeProcess1:Y", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(40, "FakeProcess2", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(50, "FakeProcess2:X", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)
    addDeviceWithProcess(device, createProcess(60, "FakeProcess2:Y", Common.Process.State.ALIVE, device.deviceId), myTransportService,
                         myTimer)

    // PREFERRED_PROCESS aspect should be fired via the call to set the preferred process.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(processListModel.deviceToProcesses.value).isNotEmpty()
    assertThat(processListModel.deviceToProcesses.value.size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(6)

    val deviceProcessesSorted = processListModel.getSelectedDeviceProcesses()
    assertThat(deviceProcessesSorted[0].name).isEqualTo("FakeProcess1")
    assertThat(deviceProcessesSorted[1].name).isEqualTo("FakeProcess1:X")
    assertThat(deviceProcessesSorted[2].name).isEqualTo("FakeProcess1:Y")
    assertThat(deviceProcessesSorted[3].name).isEqualTo("FakeProcess2")
    assertThat(deviceProcessesSorted[4].name).isEqualTo("FakeProcess2:X")
    assertThat(deviceProcessesSorted[5].name).isEqualTo("FakeProcess2:Y")
  }

  @Test
  fun `test device selection triggers reorder of process list`() {
    assertThat(processListModel.deviceToProcesses.value).isEmpty()

    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    // Because the preferred process was set before the processes were updated, and no device selection has been made, no reordering
    // will be done.
    myProfilers.setPreferredProcess("FakeDevice", "FakeProcess3", null)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    val process1 = createProcess(10, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId)
    val process2 = createProcess(20, "FakeProcess2", Common.Process.State.ALIVE, device.deviceId)
    val process3 = createProcess(30, "FakeProcess3", Common.Process.State.ALIVE, device.deviceId)

    addDeviceWithProcess(device, process1, myTransportService, myTimer)
    addDeviceWithProcess(device, process2, myTransportService, myTimer)
    addDeviceWithProcess(device, process3, myTransportService, myTimer)

    // As stated above, because no device selection was made, no process list was reordered using the preferred process name. The explicit
    // selection of the device, however, should trigger a proper reordering.
    processListModel.onDeviceSelection(device)

    assertThat(processListModel.getPreferredProcessName()).isEqualTo("FakeProcess3")
    assertThat(processListModel.deviceToProcesses.value).isNotEmpty()
    assertThat(processListModel.deviceToProcesses.value.size).isEqualTo(1)
    assertThat(processListModel.getSelectedDeviceProcesses().size).isEqualTo(3)
    // Make sure that despite being lexicographically greater than the "FakeProcess1", because it is the preferred process, "FakeProcess3"
    // is the first device process listed.
    assertThat(processListModel.getSelectedDeviceProcesses().first().name).isEqualTo("FakeProcess3")
  }

  companion object {
    fun addDeviceWithProcess(device: Common.Device,
                                     process: Common.Process,
                                     transportService: FakeTransportService,
                                     timer: FakeTimer) {
      transportService.addDevice(device)
      transportService.addProcess(device, process)
      timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    }

    fun createDevice(deviceName: String,
                     deviceState: Common.Device.State,
                     version: String,
                     apilevel: Int) = Common.Device.newBuilder().setDeviceId(deviceName.hashCode().toLong()).setSerial(deviceName).setState(
      deviceState).setModel(deviceName).setVersion(version).setApiLevel(apilevel).build()

    fun createProcess(pid: Int,
                      processName: String,
                      processState: Common.Process.State,
                      deviceId: Long,
                      exposureLevel: ExposureLevel = ExposureLevel.DEBUGGABLE) = Common.Process.newBuilder().setDeviceId(deviceId).setPid(
      pid).setName(processName).setState(processState).setExposureLevel(exposureLevel).build()
  }
}