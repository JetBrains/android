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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.StartTrace
import com.android.tools.idea.transport.faketransport.commands.StopTrace
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.singleartifact.cpu.CpuTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils.createDevice
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils.createProcess
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import perfetto.protos.PerfettoConfig
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class SystemTraceTaskHandlerTest(private val myExposureLevel: ExposureLevel) {
  private val myTimer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices().apply {
    enableTaskBasedUx(true)
  }
  private val myTransportService = FakeTransportService(myTimer, false,  ideProfilerServices.featureConfig.isTaskBasedUxEnabled)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("SystemTraceTaskHandlerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var mySystemTraceTaskHandler: SystemTraceTaskHandler

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    mySystemTraceTaskHandler = SystemTraceTaskHandler(myManager, ideProfilerServices.featureConfig.isTraceboxEnabled)
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, mySystemTraceTaskHandler)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testSupportsArtifactWithSystemTraceSessionArtifact() {
    val systemTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                    Common.Session.getDefaultInstance(),
                                                                                                    1L, 100L,
                                                                                                    createDefaultPerfettoTraceConfiguration())
    assertThat(mySystemTraceTaskHandler.supportsArtifact(systemTraceSessionArtifact)).isTrue()
  }

  @Test
  fun testSupportsArtifactWithNonSystemTraceSessionArtifact() {
    val heapProfdSessionArtifact = HeapProfdSessionArtifact(myProfilers, Common.Session.getDefaultInstance(),
                                                            Common.SessionMetaData.getDefaultInstance(),
                                                            Trace.TraceInfo.getDefaultInstance())
    assertThat(mySystemTraceTaskHandler.supportsArtifact(heapProfdSessionArtifact)).isFalse()
  }

  @Test
  fun testStartTaskInvokedOnEnterWithAliveSession() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.SYSTEM_TRACE)
    val systemTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                    Common.Session.getDefaultInstance(), 1L,
                                                                                                    100L,
                                                                                                    createDefaultPerfettoTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(systemTraceSessionArtifact)
    mySystemTraceTaskHandler.enter(cpuTaskArgs)
    // The session is alive, so startTask and thus startCapture should be called.
    assertThat(mySystemTraceTaskHandler.stage!!.recordingModel.isRecording)
  }

  @Test
  fun testStartTaskWithSetStage() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.SYSTEM_TRACE)
    // To start the task and thus the capture, the stage must be set up before. This will be taken care of via the setupStage() method call,
    // on enter of the task handler, but this test is testing the explicit invocation of startTask.
    mySystemTraceTaskHandler.setupStage()
    mySystemTraceTaskHandler.startTask()
    assertThat(mySystemTraceTaskHandler.stage!!.recordingModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithUnsetStage() {
    // To start the task and thus the capture, the stage must be set up before. Here we will test the case where startTask is invoked
    // without the stage being set precondition being met.
    val exception = assertFailsWith<Throwable> {
      mySystemTraceTaskHandler.startTask()
    }
    assertThat(mySystemTraceTaskHandler.stage).isNull()
    assertThat(exception.message).isEqualTo("There was an error with the System Trace task. Error message: Cannot start the task as the " +
                                            "InterimStage was null.")
  }

  @Test
  fun testStopTaskSuccessfullyTerminatesRecording() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.SYSTEM_TRACE)
    // First start the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE) as StartTrace)
      .startStatus = Trace.TraceStartStatus.newBuilder()
      .setStatus(Trace.TraceStartStatus.Status.SUCCESS)
      .build()
    mySystemTraceTaskHandler.setupStage()
    mySystemTraceTaskHandler.startTask()
    assertThat(mySystemTraceTaskHandler.stage!!.recordingModel.isRecording).isTrue()

    // Wait for successful start event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Stop the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE) as StopTrace)
      .stopStatus = Trace.TraceStopStatus.newBuilder()
      .setStatus(Trace.TraceStopStatus.Status.SUCCESS)
      .build()
    mySystemTraceTaskHandler.stopTask()
    // Wait for successful end event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(mySystemTraceTaskHandler.stage!!.recordingModel.isRecording).isFalse()
  }

  @Test
  fun testStopTaskSuccessfullyTerminatesTaskSession() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.SYSTEM_TRACE)
    // First start the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE) as StartTrace)
      .startStatus = Trace.TraceStartStatus.newBuilder()
      .setStatus(Trace.TraceStartStatus.Status.SUCCESS)
      .build()
    mySystemTraceTaskHandler.setupStage()
    mySystemTraceTaskHandler.startTask()
    assertThat(myManager.isSessionAlive).isTrue()

    // Wait for successful start event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Stop the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE) as StopTrace)
      .stopStatus = Trace.TraceStopStatus.newBuilder()
      .setStatus(Trace.TraceStopStatus.Status.SUCCESS)
      .build()
    mySystemTraceTaskHandler.stopTask()
    // Wait for successful end event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Issuing a stop trace command should result in the session terminating as well.
    assertThat(myManager.isSessionAlive).isFalse()
  }

  @Test
  fun testLoadTaskInvokedOnEnterWithDeadSession() {
    TaskHandlerTestUtils.startAndStopSession(myExposureLevel, myProfilers, myManager, myTransportService, myTimer,
                                             Common.ProfilerTaskType.SYSTEM_TRACE)

    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    // Create a fake CpuCaptureSessionArtifact that uses a Perfetto (System Trace) configuration.
    val systemTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                    Common.Session.getDefaultInstance(), 1L,
                                                                                                    100L,
                                                                                                    createDefaultPerfettoTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(systemTraceSessionArtifact)
    // The session is not alive (dead) so loadTask and thus loadCapture should be called.
    val argsSuccessfullyUsed = mySystemTraceTaskHandler.enter(cpuTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior is called by checking if the stage was set to CpuProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNonNullTaskArgs() {
    TaskHandlerTestUtils.startAndStopSession(myExposureLevel, myProfilers, myManager, myTransportService, myTimer,
                                             Common.ProfilerTaskType.SYSTEM_TRACE)

    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    val systemTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                    Common.Session.getDefaultInstance(), 1L,
                                                                                                    100L,
                                                                                                    createDefaultPerfettoTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(systemTraceSessionArtifact)
    val argsSuccessfullyUsed = mySystemTraceTaskHandler.loadTask(cpuTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior was called by checking if the stage was set to CpuProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNullTaskArgs() {
    TaskHandlerTestUtils.startAndStopSession(myExposureLevel, myProfilers, myManager, myTransportService, myTimer,
                                             Common.ProfilerTaskType.SYSTEM_TRACE)

    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    val exception = assertFailsWith<Throwable> {
      mySystemTraceTaskHandler.loadTask(null)
    }

    assertThat(exception.message).isEqualTo("There was an error with the System Trace task. Error message: The task arguments (TaskArgs) " +
                                            "supplied are not of the expected type (CpuTaskArgs).")

    // Verify that the artifact doSelect behavior was not called by checking if the stage was not set to CpuProfilerStage.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testCreateArgsSuccessfully() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to SessionArtifactUtils.createSessionItem(myProfilers, selectedSession, 1, listOf(
        SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers, selectedSession, 1, 100L,
                                                                       5L, 500L,
                                                                       createDefaultPerfettoTraceConfiguration()))),
    )

    val cpuTaskArgs = mySystemTraceTaskHandler.createArgs(sessionIdToSessionItems, selectedSession)
    assertThat(cpuTaskArgs).isNotNull()
    assertThat(cpuTaskArgs).isInstanceOf(CpuTaskArgs::class.java)
    assertThat(cpuTaskArgs!!.getCpuCaptureArtifact()).isNotNull()
    assertThat(cpuTaskArgs.getCpuCaptureArtifact().artifactProto.configuration.hasPerfettoOptions()).isTrue()
    assertThat(cpuTaskArgs.getCpuCaptureArtifact().artifactProto.fromTimestamp).isEqualTo(5L)
    assertThat(cpuTaskArgs.getCpuCaptureArtifact().artifactProto.toTimestamp).isEqualTo(500L)
  }

  @Test
  fun testCreateArgsFails() {
    // By setting a session id that does not match any of the session items, the task artifact will not be found in the call to createArgs
    // will fail to be constructed.
    val selectedSession = Common.Session.newBuilder().setSessionId(0).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to SessionArtifactUtils.createSessionItem(myProfilers, selectedSession, 1, listOf(
        SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers, selectedSession, 1, 100L,
                                                                       5L, 500L,
                                                                       createDefaultPerfettoTraceConfiguration()))),
    )

    val cpuTaskArgs = mySystemTraceTaskHandler.createArgs(sessionIdToSessionItems, selectedSession)
    // A return value of null indicates the task args were not constructed correctly (the underlying artifact was not found or supported by
    // the task).
    assertThat(cpuTaskArgs).isNull()
  }

  @Test
  fun testSupportsDeviceAndProcessWithTraceboxDisabled() {
    mySystemTraceTaskHandler = SystemTraceTaskHandler(myManager, false)
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // System Trace requires device with AndroidVersion P or above if tracebox is disabled.
    val mDevice = createDevice(AndroidVersion.VersionCodes.M)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(mDevice, process)).isFalse()
    val nDevice = createDevice(AndroidVersion.VersionCodes.N)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(nDevice, process)).isFalse()
    val oDevice = createDevice(AndroidVersion.VersionCodes.O)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(oDevice, process)).isFalse()
    val pDevice = createDevice(AndroidVersion.VersionCodes.P)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(pDevice, process)).isTrue()
    val qDevice = createDevice(AndroidVersion.VersionCodes.Q)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(qDevice, process)).isTrue()
  }

  @Test
  fun testSupportsDeviceAndProcessWithTraceboxEnabled() {
    mySystemTraceTaskHandler = SystemTraceTaskHandler(myManager, true)
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // System Trace requires device with AndroidVersion P or above if tracebox is enabled.
    val lDevice = createDevice(AndroidVersion.VersionCodes.LOLLIPOP)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(lDevice, process)).isFalse()
    val mDevice = createDevice(AndroidVersion.VersionCodes.M)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(mDevice, process)).isTrue()
    val nDevice = createDevice(AndroidVersion.VersionCodes.N)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(nDevice, process)).isTrue()
  }

  @Test
  fun testGetTaskName() {
    assertThat(mySystemTraceTaskHandler.getTaskName()).isEqualTo("System Trace")
  }

  private fun createDefaultPerfettoTraceConfiguration() = Trace.TraceConfiguration.newBuilder().setPerfettoOptions(
    PerfettoConfig.TraceConfig.getDefaultInstance()).build()

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<ExposureLevel> {
      return listOf(ExposureLevel.DEBUGGABLE, ExposureLevel.PROFILEABLE)
    }
  }
}