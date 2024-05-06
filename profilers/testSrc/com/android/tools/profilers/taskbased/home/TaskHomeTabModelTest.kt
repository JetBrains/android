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
package com.android.tools.profilers.taskbased.home

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.addDeviceWithProcess
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createDevice
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createProcess
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TaskHomeTabModelTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskHomeTabModelTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var taskHomeTabModel: TaskHomeTabModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideProfilerServices, myTimer)
    myManager = myProfilers.sessionsManager
    taskHomeTabModel = TaskHomeTabModel(myProfilers)
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Test
  fun `test retrieval of most recent task type selection`() {
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test retrieval of most recent device selection`() {
    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    taskHomeTabModel.processListModel.onDeviceSelection(device)
    assertThat(taskHomeTabModel.selectedDevice).isEqualTo(device)
  }

  @Test
  fun `test retrieval of most recent process selection`() {
    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    taskHomeTabModel.processListModel.onDeviceSelection(device)
    val process = createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, device.deviceId)
    addDeviceWithProcess(device, process, myTransportService, myTimer)
    taskHomeTabModel.processListModel.onProcessSelection(process)
    assertThat(taskHomeTabModel.selectedProcess).isEqualTo(process)
  }

  @Test
  fun `test task type selection resets after device selection`() {
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    taskHomeTabModel.processListModel.onDeviceSelection(device)

    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }

  @Test
  fun `test task type selection resets after process selection`() {
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    val process = createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, 0)
    taskHomeTabModel.processListModel.onProcessSelection(process)

    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }
}