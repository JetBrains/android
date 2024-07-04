/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.TaskSelectionVerificationUtils.getStartTaskError
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils.createProfilerDeviceSelection
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule

class StartTaskSelectionVerificationTest {
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
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach {  myProfilers.addTaskHandler(it.key, it.value)  }
    taskHomeTabModel = TaskHomeTabModel(myProfilers)
  }

  @Test
  fun testInvalidDevice() {
    val error = getStartTaskError(
      ProfilerTaskType.SYSTEM_TRACE,
      // Set an invalid device selection.
      createDeviceSelection(isValid = false),
      createProcessSelection(isValid = true, isAlive = true),
      TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.INVALID_DEVICE, error)
  }

  @Test
  fun testInvalidProcess() {
    val error = getStartTaskError(
      ProfilerTaskType.SYSTEM_TRACE,
      createDeviceSelection(isValid = true),
      // Set an invalid process selection.
      createProcessSelection(isValid = false),
      TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.INVALID_PROCESS, error)
  }

  @Test
  fun testInvalidTask() {
    val error = getStartTaskError(
      // Set an invalid task selection (UNSPECIFIED task type).
      ProfilerTaskType.UNSPECIFIED,
      createDeviceSelection(isValid = true),
      createProcessSelection(isValid = true, isAlive = true),
      TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.INVALID_TASK, error)
  }

  @Test
  fun testPreferredProcessNotSelectedForStartupTask() {
    // The following test does not, at the moment, ever occur in the UI because if the user has not selected the preferred process, the
    // profiler will automatically switch the task starting point dropdown selection to "Now", preventing this error. This test (and the
    // error is tests) is here as coverage for if that auto-selection behavior is ever removed.
    myProfilers.preferredProcessName = "fake.name"
    val error = getStartTaskError(
      ProfilerTaskType.SYSTEM_TRACE,
      createDeviceSelection(isValid = true),
      // A valid process selection is made, but the process name is set to be different from the preferred process.
      createProcessSelection(isValid = true, name = "name.fake"),
      // Make sure that this error only shows when the user has selected startup tasks.
      TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.PREFERRED_PROCESS_NOT_SELECTED_FOR_STARTUP_TASK, error)
  }

  @Test
  fun testTaskUnsupportedOnStartup() {
    // This error can occur when the user selects a dead preferred process, and a task that does not support starting from process start.
    myProfilers.preferredProcessName = "fake.name"
    val error = getStartTaskError(
      // Heap dump is a task that is unsupported on startup
      ProfilerTaskType.HEAP_DUMP,
      createDeviceSelection(true),
      // Set the process name to match the preferred process so that starting a task from process start is enabled for the offline process.
      createProcessSelection(true, isAlive = false, name = "fake.name"),
      TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.TASK_UNSUPPORTED_ON_STARTUP, error)
  }

  @Test
  fun testStartupTaskUsingUnsupportedDevice() {
    // This error can occur when the user selects a dead preferred process, and a task that does not support starting from process start.
    myProfilers.preferredProcessName = "fake.name"
    val systemTraceError = getStartTaskError(
      ProfilerTaskType.SYSTEM_TRACE,
      // System trace, callstack sample, and java/kotlin method recording tasks required device with api >= 26
      createDeviceSelection(true, featureLevel = 25),
      // Set the process name to match the preferred process so that starting a task from process start is enabled for the offline process.
      createProcessSelection(true, isAlive = false, name = "fake.name"),
      TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.STARTUP_TASK_USING_UNSUPPORTED_DEVICE, systemTraceError)

    val nativeAllocationsError = getStartTaskError(
      ProfilerTaskType.NATIVE_ALLOCATIONS,
      // Native allocations task requires device with api >= 29
      createDeviceSelection(true, featureLevel = 28),
      // Set the process name to match the preferred process so that starting a task from process start is enabled for the offline process.
      createProcessSelection(true, isAlive = false, name = "fake.name"),
      TaskHomeTabModel.ProfilingProcessStartingPoint.PROCESS_START,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.STARTUP_TASK_USING_UNSUPPORTED_DEVICE, nativeAllocationsError)
  }

  @Test
  fun testDeviceSelectionIsOffline() {
    // The error for the device selection being offline is only shown when the user tries to perform a task from "Now".
    val error = getStartTaskError(
      ProfilerTaskType.SYSTEM_TRACE,
      // Device selection is valid, but offline.
      createDeviceSelection(isValid = true, isRunning = false),
      createProcessSelection(isValid = true),
      TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.DEVICE_SELECTION_IS_OFFLINE, error)
  }

  @Test
  fun testTaskUnsupportedByProcess() {
    // The error a process not supporting a task is only shown when the user tries to perform a task from "Now".
    val error = getStartTaskError(
      ProfilerTaskType.HEAP_DUMP,
      // Device selection is valid and online (required to start a task from "Now").
      createDeviceSelection(isValid = true, isRunning = true),
      // Heap dump task is not supported by a profileable process.
      createProcessSelection(isValid = true, exposureLevel = ExposureLevel.PROFILEABLE),
      TaskHomeTabModel.ProfilingProcessStartingPoint.NOW,
      myProfilers
    )
    assertEquals(StartTaskSelectionError.TASK_UNSUPPORTED_BY_DEVICE_OR_PROCESS, error)
  }

  private fun createDeviceSelection(isValid: Boolean,
                                    isRunning: Boolean = false,
                                    featureLevel: Int = 0) = if (isValid) createProfilerDeviceSelection(featureLevel, isRunning) else null

  private fun createProcessSelection(isValid: Boolean,
                                     isAlive: Boolean = false,
                                     name: String = "",
                                     exposureLevel: ExposureLevel = ExposureLevel.DEBUGGABLE) =
    if (isValid) TaskModelTestUtils.createProcess(123, name, if (isAlive) Common.Process.State.ALIVE else Common.Process.State.DEAD, 456L,
                                                  exposureLevel)
    else Common.Process.getDefaultInstance()
}
