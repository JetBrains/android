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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.TaskEntranceTabModel.Companion.HIDE_NEW_TASK_PROMPT
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromNow
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.canTaskStartFromProcessStart
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.addDeviceWithProcess
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createDevice
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createProcess
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createProfilerDeviceSelection
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.CallstackSampleTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.HeapDumpTaskHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myProfilers.sessionsManager)
    taskHandlers.forEach { (type, handler) -> myProfilers.addTaskHandler(type, handler) }
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
    assertThat(taskHomeTabModel.selectedDevice).isEqualTo(ProfilerDeviceSelection(device.model, 0, true, device))
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
  fun `test task type selection remains after device selection`() {
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    val device = createDevice("FakeDevice", Common.Device.State.ONLINE)
    taskHomeTabModel.processListModel.onDeviceSelection(device)

    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test task type selection remains after process selection`() {
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    val process = createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, 0)
    taskHomeTabModel.processListModel.onProcessSelection(process)

    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test canTaskStartFromNow with valid selections`() {
    // Create device that can support callstack sampling (api level > O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.O, true)
    // Create an alive process
    val selectedProcess = Common.Process.newBuilder().setState(Common.Process.State.ALIVE).setExposureLevel(
      Common.Process.ExposureLevel.PROFILEABLE).build()
    // Set a valid task type and add its corresponding task handler so the task handler can be used to check if the selected device and
    // process are supported
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE
    myProfilers.addTaskHandler(ProfilerTaskType.CALLSTACK_SAMPLE, CallstackSampleTaskHandler(myManager))

    // Ensure the method returns true for valid inputs
    assertTrue(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, myProfilers.taskHandlers))
  }

  @Test
  fun `test canTaskStartFromNow with default device instance selected and no process selected`() {
    // Create and set device to be default instance of Common.Device, this should cause `canTaskRunFromRunningProcess` to return false.
    // This simulates a running device selection detected from toolbar, with the corresponding device from the pipeline not found yet.
    val selectedDevice = ProfilerDeviceSelection("FakeDevice", 30, true, Common.Device.getDefaultInstance())
    // Create an empty process instance as if there is no running device from transport pipeline found, there is no process to be selected
    val selectedProcess = Common.Process.getDefaultInstance()
    // Set a valid task type and add its corresponding task handler so the task handler can be used to check if the selected device and
    // process are supported
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE
    myProfilers.addTaskHandler(ProfilerTaskType.CALLSTACK_SAMPLE, CallstackSampleTaskHandler(myManager))

    // Ensure the method returns false as the selected device contains a default instance of Common.Device
    assertFalse(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, myProfilers.taskHandlers))
  }

  @Test
  fun `test canTaskStartFromNow with dead process`() {
    // Create device that can support callstack sampling (api level > O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.O, true)
    // Create an alive process
    val selectedProcess = Common.Process.newBuilder().setState(Common.Process.State.DEAD).build()
    // Set a valid task type and add its corresponding task handler so the task handler can be used to check if the selected device and
    // process are supported
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE
    myProfilers.addTaskHandler(ProfilerTaskType.CALLSTACK_SAMPLE, CallstackSampleTaskHandler(myManager))

    // Ensure the method returns false as the selected process is not alive
    assertFalse(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, myProfilers.taskHandlers))
  }

  @Test
  fun `test canTaskStartFromNow with profileable process and debuggable task`() {
    // Create and set device that can support heap dump
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.O, true)
    // Create an alive process
    val selectedProcess = Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.PROFILEABLE).setState(
      Common.Process.State.ALIVE).build()
    // Set a valid task type and add its corresponding task handler so the task handler can be used to check if the selected device and
    // process are supported
    val selectedTaskType = ProfilerTaskType.HEAP_DUMP
    myProfilers.addTaskHandler(ProfilerTaskType.HEAP_DUMP, HeapDumpTaskHandler(myManager))

    // Ensure the method returns false as the process is profileable, but the heap dump task only can use a debuggable process
    assertFalse(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, myProfilers.taskHandlers))
  }

  @Test
  fun `test canTaskStartFromNow with device not supported by task handler`() {
    // Create and set device that cannot support callstack sampling (api level < O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.N, true)
    // Ensure an alive process is selected
    val selectedProcess = Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.PROFILEABLE).setState(
      Common.Process.State.ALIVE).build()
    // Select a valid task type and add its corresponding task handler so the task handler can be used to check if the selected device and
    // process are supported
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE
    myProfilers.addTaskHandler(ProfilerTaskType.CALLSTACK_SAMPLE, CallstackSampleTaskHandler(myManager))

    // Ensure the method returns false as the device feature level (N) is less than the minimum required by the callstack task handler
    assertFalse(canTaskStartFromNow(selectedTaskType, selectedDevice, selectedProcess, myProfilers.taskHandlers))
  }

  @Test
  fun `test canTaskStartFromProcessStart with valid selections`() {
    myProfilers.preferredProcessName = "com.foo.bar"
    // Select a device that can support callstack sampling on startup (api level >= O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.O, true)
    // Select the preferred process
    val selectedProcess = Common.Process.newBuilder().setName("com.foo.bar").build()
    // Select a valid task type that is supported on startup
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE

    // Ensure the method returns true as the all selections are valid for starting a task from process start
    assertTrue(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, myProfilers))
  }

  @Test
  fun `test canTaskStartFromProcessStart with non startup-capable task`() {
    myProfilers.preferredProcessName = "com.foo.bar"
    // Create and set device that can support callstack sampling on startup (api level >= O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.O, true)
    // Select the preferred process
    val selectedProcess = Common.Process.newBuilder().setName("com.foo.bar").build()
    // Select a task type that is not supported on startup (e.g. Heap Dump)
    val selectedTaskType = ProfilerTaskType.HEAP_DUMP

    // Ensure the method returns false as the selected task is not a startup-capable task
    assertFalse(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, myProfilers))
  }

  @Test
  fun `test canTaskStartFromProcessStart with non preferred process`() {
    myProfilers.preferredProcessName = "com.foo.bar"
    // Create and set device that can support callstack sampling on startup (api level >= O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.O, true)
    // Select a process that is NOT the preferred process
    val selectedProcess = Common.Process.newBuilder().setName("not.preferred.process").build()
    // Select a valid task type that is supported on startup
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE

    // Ensure the method returns false as the selected process is not the preferred process
    assertFalse(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, myProfilers))
  }

  @Test
  fun `test canTaskStartFromProcessStart with cpu startup task not supported by device`() {
    myProfilers.preferredProcessName = "com.foo.bar"
    // Create and set device that cannot support callstack sampling on startup (api level < O)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.N, true)
    // Select the preferred process
    val selectedProcess = Common.Process.newBuilder().setName("com.foo.bar").build()
    // Select a valid cpu task type that is supported on startup
    val selectedTaskType = ProfilerTaskType.CALLSTACK_SAMPLE

    // Ensure the method returns false as the device feature level (N) is less than the min required by the callstack startup task
    assertFalse(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, myProfilers))
  }

  @Test
  fun `test canTaskStartFromProcessStart with memory startup task not supported by device`() {
    myProfilers.preferredProcessName = "com.foo.bar"
    // Create and set device that cannot support callstack sampling on startup (api level < Q)
    val selectedDevice = createProfilerDeviceSelection(AndroidVersion.VersionCodes.P, true)
    // Select the preferred process
    val selectedProcess = Common.Process.newBuilder().setName("com.foo.bar").build()
    // Select a valid memory task type that is supported on startup
    val selectedTaskType = ProfilerTaskType.NATIVE_ALLOCATIONS

    // Ensure the method returns false as the device feature level (P) is less than the min required by the native allocations startup task
    assertFalse(canTaskStartFromProcessStart(selectedTaskType, selectedDevice, selectedProcess, myProfilers))
  }

  @Test
  fun `test changing process selection maintains task selection`() {
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.CALLSTACK_SAMPLE)
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.CALLSTACK_SAMPLE)

    taskHomeTabModel.processListModel.onProcessSelection(Common.Process.newBuilder().setName("FakeProcess").build())
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.CALLSTACK_SAMPLE)
  }

  @Test
  fun `test starting new task with ongoing task attempts stop of ongoing task`() {
    // Create and select the device
    val selectedDevice = createDevice("FakeDevice", Common.Device.State.ONLINE, "12", AndroidVersion.VersionCodes.S)
    taskHomeTabModel.processListModel.onDeviceSelection(selectedDevice)
    // Create and select the process
    val selectedProcess = createProcess(20, "FakeProcess1", Common.Process.State.ALIVE, selectedDevice.deviceId)
    addDeviceWithProcess(selectedDevice, selectedProcess, myTransportService, myTimer)
    taskHomeTabModel.processListModel.onProcessSelection(selectedProcess)

    // Select the task and populate the respective task handler
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.CALLSTACK_SAMPLE)
    assertTrue(canTaskStartFromNow(ProfilerTaskType.CALLSTACK_SAMPLE,
                                   ProfilerDeviceSelection("FakeDevice", selectedDevice.featureLevel, true, selectedDevice),
                                   selectedProcess, myProfilers.taskHandlers))

    // Populate current task handler to simulate valid state (due to lack of interface with tool window code, the current task handler can
    // not be set when the task is started.
    myProfilers.setCurrentTaskHandlerFetcher { myProfilers.taskHandlers[ProfilerTaskType.CALLSTACK_SAMPLE] }

    // Start the task (this will not actually launch the task tab, it will only start the underlying session).
    taskHomeTabModel.onEnterTaskButtonClick()

    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myProfilers.sessionsManager.isSessionAlive).isTrue()
    assertThat(myProfilers.sessionsManager.currentTaskType).isEqualTo(ProfilerTaskType.CALLSTACK_SAMPLE)

    // Attempt start of new task (should attempt to stop previous, Callstack Sample task)
    taskHomeTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.NATIVE_ALLOCATIONS)
    assertTrue(canTaskStartFromNow(ProfilerTaskType.NATIVE_ALLOCATIONS,
                                   ProfilerDeviceSelection("FakeDevice", selectedDevice.featureLevel, true, selectedDevice),
                                   selectedProcess, myProfilers.taskHandlers))

    // Because it is difficult to simulate the launch of an actual task recording (as it requires launching a new tab), attempting a new
    // task cant actually stop the previous task recording. However, to verify that the previous task recording was attempted to be stopped,
    // the following assertion error can be expected on start of a new task.
    val e = assertThrows(AssertionError::class.java) {
      taskHomeTabModel.onEnterTaskButtonClick()
    }
    // Make sure that it attempted to stop the current/ongoing task (Callstack Sample)
    assertThat(e.message).isEqualTo(
      "There was an error with the Callstack Sample task. Error message: Cannot stop the task as the InterimStage was null.")
  }
}