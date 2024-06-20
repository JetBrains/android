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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.StartTrace
import com.android.tools.idea.transport.faketransport.commands.StopTrace
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Trace
import com.android.tools.profiler.proto.Trace.TraceMode
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
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class JavaKotlinMethodRecordingTaskHandlerTest(private val myExposureLevel: ExposureLevel) {
  private val myTimer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices().apply {
    enableTaskBasedUx(true)
  }
  private val myTransportService = FakeTransportService(myTimer, false,  ideProfilerServices.featureConfig.isTaskBasedUxEnabled)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("JavaKotlinMethodTraceTaskHandlerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myJavaKotlinMethodRecordingTaskHandler: JavaKotlinMethodRecordingTaskHandler

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    myJavaKotlinMethodRecordingTaskHandler = JavaKotlinMethodRecordingTaskHandler(myManager)
    myProfilers.addTaskHandler(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING, myJavaKotlinMethodRecordingTaskHandler)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testSupportsArtifactWithJavaKotlinMethodTraceSessionArtifact() {
    val javaKotlinMethodTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                              Common.Session.getDefaultInstance(),
                                                                                                              1L, 100L,
                                                                                                              createDefaultArtInstrumentedTraceConfiguration())
    assertThat(myJavaKotlinMethodRecordingTaskHandler.supportsArtifact(javaKotlinMethodTraceSessionArtifact)).isTrue()
  }

  @Test
  fun testSupportsArtifactWithNonJavaKotlinMethodTraceSessionArtifact() {
    val heapProfdSessionArtifact = HeapProfdSessionArtifact(myProfilers, Common.Session.getDefaultInstance(),
                                                            Common.SessionMetaData.getDefaultInstance(),
                                                            Trace.TraceInfo.getDefaultInstance())
    assertThat(myJavaKotlinMethodRecordingTaskHandler.supportsArtifact(heapProfdSessionArtifact)).isFalse()
  }

  @Test
  fun testStartTaskInvokedOnEnterWithAliveSession() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)
    val javaKotlinMethodTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                              Common.Session.getDefaultInstance(),
                                                                                                              1L,
                                                                                                              100L,
                                                                                                              createDefaultArtInstrumentedTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(false, javaKotlinMethodTraceSessionArtifact)
    myJavaKotlinMethodRecordingTaskHandler.enter(cpuTaskArgs)
    // The session is alive, so startTask and thus startCapture should be called.
    assertThat(myJavaKotlinMethodRecordingTaskHandler.stage!!.recordingModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithSetStage() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)
    // To start the task and thus the capture, the stage must be set up before. This will be taken care of via the setupStage() method call,
    // on enter of the task handler, but this test is testing the explicit invocation of startTask.
    myJavaKotlinMethodRecordingTaskHandler.setupStage()
    myJavaKotlinMethodRecordingTaskHandler.startTask(CpuTaskArgs(false, null))
    assertThat(myJavaKotlinMethodRecordingTaskHandler.stage!!.recordingModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithUnsetStage() {
    // To start the task and thus the capture, the stage must be set up before. Here we will test the case where startTask is invoked
    // without the stage being set precondition being met.
    val exception = assertFailsWith<Throwable> {
      myJavaKotlinMethodRecordingTaskHandler.startTask(CpuTaskArgs(false, null))
    }
    assertThat(myJavaKotlinMethodRecordingTaskHandler.stage).isNull()
    assertThat(exception.message).isEqualTo(
      "There was an error with the Java/Kotlin Method Recording task. Error message: Cannot start the task as the InterimStage was null.")
  }

  @Test
  fun testStopTaskSuccessfullyTerminatesRecording() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)
    // First start the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE) as StartTrace)
      .startStatus = Trace.TraceStartStatus.newBuilder()
      .setStatus(Trace.TraceStartStatus.Status.SUCCESS)
      .build()
    myJavaKotlinMethodRecordingTaskHandler.setupStage()
    myJavaKotlinMethodRecordingTaskHandler.startTask(CpuTaskArgs(false, null))
    assertThat(myJavaKotlinMethodRecordingTaskHandler.stage!!.recordingModel.isRecording).isTrue()

    // Wait for successful start event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Stop the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE) as StopTrace)
      .stopStatus = Trace.TraceStopStatus.newBuilder()
      .setStatus(Trace.TraceStopStatus.Status.SUCCESS)
      .build()
    myJavaKotlinMethodRecordingTaskHandler.stopTask()
    // Wait for successful end event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(myJavaKotlinMethodRecordingTaskHandler.stage!!.recordingModel.isRecording).isFalse()
  }

  @Test
  fun testStopTaskSuccessfullyTerminatesTaskSession() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)
    // First start the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE) as StartTrace)
      .startStatus = Trace.TraceStartStatus.newBuilder()
      .setStatus(Trace.TraceStartStatus.Status.SUCCESS)
      .build()
    myJavaKotlinMethodRecordingTaskHandler.setupStage()
    myJavaKotlinMethodRecordingTaskHandler.startTask(CpuTaskArgs(false, null))
    assertThat(myManager.isSessionAlive).isTrue()

    // Wait for successful start event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Stop the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE) as StopTrace)
      .stopStatus = Trace.TraceStopStatus.newBuilder()
      .setStatus(Trace.TraceStopStatus.Status.SUCCESS)
      .build()
    myJavaKotlinMethodRecordingTaskHandler.stopTask()
    // Wait for successful end event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Issuing a stop trace command should result in the session terminating as well.
    assertThat(myManager.isSessionAlive).isFalse()
  }

  @Test
  fun testLoadTaskInvokedOnEnterWithDeadSession() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    // Create a fake CpuCaptureSessionArtifact that uses an ART Instrumented (Java/Kotlin Method Sample) configuration.
    val javaKotlinMethodTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                              Common.Session.getDefaultInstance(),
                                                                                                              1L,
                                                                                                              100L,
                                                                                                              createDefaultArtInstrumentedTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(false, javaKotlinMethodTraceSessionArtifact)
    // The session is not alive (dead) so loadTask and thus loadCapture should be called.
    val argsSuccessfullyUsed = myJavaKotlinMethodRecordingTaskHandler.enter(cpuTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior is called by checking if the stage was set to CpuProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNonNullTaskArgs() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    val javaKotlinMethodTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                              Common.Session.getDefaultInstance(),
                                                                                                              1L,
                                                                                                              100L,
                                                                                                              createDefaultArtInstrumentedTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(false, javaKotlinMethodTraceSessionArtifact)
    val argsSuccessfullyUsed = myJavaKotlinMethodRecordingTaskHandler.loadTask(cpuTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior was called by checking if the stage was set to CpuProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNullTaskArgsArtifact() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    val exception = assertFailsWith<Throwable> {
      myJavaKotlinMethodRecordingTaskHandler.loadTask(CpuTaskArgs(false, null))
    }

    assertThat(exception.message).isEqualTo(
      "There was an error with the Java/Kotlin Method Recording task. Error message: The task arguments (CpuTaskArgs) supplied do not " +
      "contains a valid artifact to load.")

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
                                                                       createDefaultArtInstrumentedTraceConfiguration()))),
    )

    val cpuTaskArgs = myJavaKotlinMethodRecordingTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    assertThat(cpuTaskArgs).isNotNull()
    assertThat(cpuTaskArgs).isInstanceOf(CpuTaskArgs::class.java)
    cpuTaskArgs as CpuTaskArgs
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()).isNotNull()
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()!!.artifactProto.configuration.hasArtOptions()).isTrue()
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()!!.artifactProto.configuration.artOptions.traceMode).isEqualTo(TraceMode.INSTRUMENTED)
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()!!.artifactProto.fromTimestamp).isEqualTo(5L)
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()!!.artifactProto.toTimestamp).isEqualTo(500L)
  }

  @Test
  fun testCreateArgsFailsToFindArtifact() {
    // By setting a session id that does not match any of the session items, the task artifact will not be found in the call to createArgs
    // will fail to be constructed.
    val selectedSession = Common.Session.newBuilder().setSessionId(0).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to SessionArtifactUtils.createSessionItem(myProfilers, selectedSession, 1, listOf(
        SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers, selectedSession, 1, 100L,
                                                                       5L, 500L,
                                                                       createDefaultArtInstrumentedTraceConfiguration()))),
    )

    assertThrows(IllegalStateException::class.java) {
      myJavaKotlinMethodRecordingTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    }
  }

  @Test
  fun testSupportsDeviceAndProcess() {
    // Java/Kotlin Method Sample requires device with AndroidVersion 0 or above (all devices).
    val minVersionDevice = TaskHandlerTestUtils.createDevice(1)
    val process = TaskHandlerTestUtils.createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    assertThat(myJavaKotlinMethodRecordingTaskHandler.supportsDeviceAndProcess(minVersionDevice, process)).isTrue()
  }

  @Test
  fun testGetTaskName() {
    assertThat(myJavaKotlinMethodRecordingTaskHandler.getTaskName()).isEqualTo("Java/Kotlin Method Recording")
  }

  private fun createDefaultArtInstrumentedTraceConfiguration() = Trace.TraceConfiguration.newBuilder().setArtOptions(
    Trace.ArtOptions.newBuilder().setTraceMode(TraceMode.INSTRUMENTED).build()).build()

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<ExposureLevel> {
      return listOf(ExposureLevel.DEBUGGABLE, ExposureLevel.PROFILEABLE)
    }
  }
}