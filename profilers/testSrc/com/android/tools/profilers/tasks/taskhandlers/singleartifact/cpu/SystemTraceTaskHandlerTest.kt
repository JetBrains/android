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
import com.android.testutils.MockitoKt
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
import com.android.tools.profilers.cpu.config.AtraceConfiguration
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel.ProfilerDeviceSelection
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.singleartifact.cpu.CpuTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils.createDevice
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils.createProcess
import com.google.common.truth.Truth.assertThat
import io.ktor.util.reflect.instanceOf
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.spy
import perfetto.protos.PerfettoConfig
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
  fun testGetCpuRecordingConfigAtraceLessThanP() {
    // (withTraceBoxDisabled) If device is set and device level is less than 28, return AtraceConfiguration
    val mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(
      createFakeDevice(AndroidVersion.VersionCodes.N), false)
    // Simulate ongoing recording so that config is set.
    MockitoKt.whenever(mySystemTraceTaskHandlerMock.sessionsManager.isSessionAlive).thenReturn(true)
    mySystemTraceTaskHandlerMock.setupStage()
    val cpuProfilerStage = mySystemTraceTaskHandlerMock.stage as CpuProfilerStage
    assertTrue { cpuProfilerStage.profilerConfigModel.profilingConfiguration.instanceOf (AtraceConfiguration::class)}
  }

  @Test
  fun testGetCpuRecordingConfigPerfettoAtleastP() {
    // (withTraceBoxDisabled) If device is set and device level is greater than 28, return PerfettoSystemTraceConfiguration
    val mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(
      createFakeDevice(AndroidVersion.VersionCodes.R), false)
    // Simulate ongoing recording so that config is set.
    MockitoKt.whenever(mySystemTraceTaskHandlerMock.sessionsManager.isSessionAlive).thenReturn(true)
    mySystemTraceTaskHandlerMock.setupStage()
    val cpuProfilerStage = mySystemTraceTaskHandlerMock.stage as CpuProfilerStage
    assertTrue { cpuProfilerStage.profilerConfigModel.profilingConfiguration.instanceOf (PerfettoSystemTraceConfiguration::class)}
  }

  @Test
  fun testCpuConfigIsNotSetIfTaskIsNotPerformingANewRecording() {
    // (withTraceBoxDisabled) If device is set and device level is greater than 28, return PerfettoSystemTraceConfiguration
    val mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(
      createFakeDevice(AndroidVersion.VersionCodes.R), false)
    // Simulate no ongoing recording, should not set the config as a consequence.
    MockitoKt.whenever(mySystemTraceTaskHandlerMock.sessionsManager.isSessionAlive).thenReturn(false)
    // Should not throw an exception.
    mySystemTraceTaskHandlerMock.setupStage()
  }

  @Test
  fun testGetCpuRecordingConfigPerfettoWithM() {
    // (withTraceBoxEnabled) If device is set and device level is greater than 22, return PerfettoSystemTraceConfiguration
    val mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(
      createFakeDevice(AndroidVersion.VersionCodes.M), true)
    // Simulate ongoing recording so that config is set.
    MockitoKt.whenever(mySystemTraceTaskHandlerMock.sessionsManager.isSessionAlive).thenReturn(true)
    mySystemTraceTaskHandlerMock.setupStage()
    val cpuProfilerStage = mySystemTraceTaskHandlerMock.stage as CpuProfilerStage
    assertTrue { cpuProfilerStage.profilerConfigModel.profilingConfiguration.instanceOf (PerfettoSystemTraceConfiguration::class)}
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
    selectDevice(createFakeDevice(29))
    val cpuTaskArgs = CpuTaskArgs(false, systemTraceSessionArtifact)
    mySystemTraceTaskHandler.enter(cpuTaskArgs)
    // The session is alive, so startTask and thus startCapture should be called.
    assertThat(mySystemTraceTaskHandler.stage!!.recordingModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithSetStage() {
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.SYSTEM_TRACE)
    // Explicitly set the device to be used to simulate reading device selection from main toolbar.
    selectDevice(createFakeDevice(29))
    // To start the task and thus the capture, the stage must be set up before. This will be taken care of via the setupStage() method call,
    // on enter of the task handler, but this test is testing the explicit invocation of startTask.
    mySystemTraceTaskHandler.setupStage()
    mySystemTraceTaskHandler.startTask(CpuTaskArgs(false, null))
    assertThat(mySystemTraceTaskHandler.stage!!.recordingModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithUnsetStage() {
    // To start the task and thus the capture, the stage must be set up before. Here we will test the case where startTask is invoked
    // without the stage being set precondition being met.
    val exception = assertFailsWith<Throwable> {
      mySystemTraceTaskHandler.startTask(CpuTaskArgs(false, null))
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
    // Explicitly set the device to be used to simulate reading device selection from main toolbar.
    selectDevice(createFakeDevice(29))
    mySystemTraceTaskHandler.setupStage()
    mySystemTraceTaskHandler.startTask(CpuTaskArgs(false, null))
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
    selectDevice(createFakeDevice(29))
    TaskHandlerTestUtils.startSession(myExposureLevel, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.SYSTEM_TRACE)
    // First start the task successfully.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE) as StartTrace)
      .startStatus = Trace.TraceStartStatus.newBuilder()
      .setStatus(Trace.TraceStartStatus.Status.SUCCESS)
      .build()
    // Explicitly set the device to be used to simulate reading device selection from main toolbar.
    selectDevice(createFakeDevice(29))
    mySystemTraceTaskHandler.setupStage()
    mySystemTraceTaskHandler.startTask(CpuTaskArgs(false, null))
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
    selectDevice(createFakeDevice(29))
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    // Create a fake CpuCaptureSessionArtifact that uses a Perfetto (System Trace) configuration.
    val systemTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                    Common.Session.getDefaultInstance(), 1L,
                                                                                                    100L,
                                                                                                    createDefaultPerfettoTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(false, systemTraceSessionArtifact)
    // The session is not alive (dead) so loadTask and thus loadCapture should be called.
    val argsSuccessfullyUsed = mySystemTraceTaskHandler.enter(cpuTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior is called by checking if the stage was set to CpuProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNonNullTaskArgs() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    val systemTraceSessionArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers,
                                                                                                    Common.Session.getDefaultInstance(), 1L,
                                                                                                    100L,
                                                                                                    createDefaultPerfettoTraceConfiguration())
    val cpuTaskArgs = CpuTaskArgs(false, systemTraceSessionArtifact)
    val argsSuccessfullyUsed = mySystemTraceTaskHandler.loadTask(cpuTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior was called by checking if the stage was set to CpuProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(CpuProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNullTaskArgsArtifact() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(CpuProfilerStage::class.java)

    val exception = assertFailsWith<Throwable> {
      mySystemTraceTaskHandler.loadTask(CpuTaskArgs(false, null))
    }

    assertThat(exception.message).isEqualTo("There was an error with the System Trace task. Error message: The task arguments " +
                                            "(CpuTaskArgs) supplied do not contains a valid artifact to load.")

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

    val cpuTaskArgs = mySystemTraceTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    assertThat(cpuTaskArgs).isNotNull()
    assertThat(cpuTaskArgs).isInstanceOf(CpuTaskArgs::class.java)
    cpuTaskArgs as CpuTaskArgs
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()).isNotNull()
    assertThat(cpuTaskArgs.getCpuCaptureArtifact()!!.artifactProto.configuration.hasPerfettoOptions()).isTrue()
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
                                                                       createDefaultPerfettoTraceConfiguration()))),
    )

    assertThrows(IllegalStateException::class.java) {
      mySystemTraceTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    }
  }

  @Test
  fun testSupportsDeviceAndProcessWithTraceboxDisabled() {
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // System Trace requires device with AndroidVersion N or above if tracebox is disabled.
    val mDevice = createDevice(AndroidVersion.VersionCodes.M)
    var mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(mDevice, false)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(mDevice, process)).isFalse()
    val nDevice = createDevice(AndroidVersion.VersionCodes.N)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(nDevice, false)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(nDevice, process)).isTrue()
    val oDevice = createDevice(AndroidVersion.VersionCodes.O)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(oDevice, false)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(oDevice, process)).isTrue()
    val pDevice = createDevice(AndroidVersion.VersionCodes.P)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(pDevice, false)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(pDevice, process)).isTrue()
    val qDevice = createDevice(AndroidVersion.VersionCodes.Q)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(qDevice, false)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(qDevice, process)).isTrue()
  }

  @Test
  fun testSupportsDeviceAndProcessWithTraceboxEnabled() {
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // System Trace requires device with AndroidVersion M or above if tracebox is enabled.
    val lDevice = createDevice(AndroidVersion.VersionCodes.LOLLIPOP)
    var mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(lDevice, true)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(lDevice, process)).isFalse()
    val mDevice = createDevice(AndroidVersion.VersionCodes.M)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(mDevice, true)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(mDevice, process)).isTrue()
    val nDevice = createDevice(AndroidVersion.VersionCodes.N)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(mDevice, true)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(nDevice, process)).isTrue()
    val rDevice = createDevice(AndroidVersion.VersionCodes.R)
    mySystemTraceTaskHandlerMock = mockDeviceInSystemTraceTaskHandler(rDevice, true)
    assertThat(mySystemTraceTaskHandlerMock.supportsDeviceAndProcess(rDevice, process)).isTrue()
  }

  @Test
  fun testConfigDoesNotSupportApi2425VirtualArmDevicesWithTraceboxDisabled() {
    // With Tracebox disabled, the min api for Perfetto's system trace config is api 28.
    // Therefore, for devices with api < 28, ATrace will be used (note: Atrace min api is 24/N).
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // Api 24 and 25 for virtual ARM devices should not support ATrace with Tracebox disabled.
    val nDevice = createDevice(24, "arm", isVirtual = true)
    // Explicitly set the device to be used to simulate reading device selection from main toolbar.
    selectDevice(nDevice)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(nDevice, process)).isFalse()
    val oDevice = createDevice(25, "arm", isVirtual = true)
    selectDevice(oDevice)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(oDevice, process)).isFalse()
  }

  @Test
  fun testConfigDoesSupportApi2425PhysicalArmDevicesWithTraceboxDisabled() {
    // With Tracebox disabled, the min api for Perfetto's system trace config is api 28.
    // Therefore, for devices with api < 28, ATrace will be used (note: Atrace min api is 24/N).
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // Api 24 and 25 for physical ARM devices should support ATrace with Tracebox disabled.
    val nDevice = createDevice(24, "arm", isVirtual = false)
    // Explicitly set the device to be used to simulate reading device selection from main toolbar.
    selectDevice(nDevice)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(nDevice, process)).isTrue()
    val oDevice = createDevice(25, "arm", isVirtual = false)
    selectDevice(oDevice)
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(oDevice, process)).isTrue()
  }

  @Test
  fun testConfigDoesNotSupportApi23AndBelow() {
    val process = createProcess(myExposureLevel == ExposureLevel.PROFILEABLE)
    // Api 23 devices should not be supported by either ATrace or Perfetto.
    val mDevice = createDevice(23, "arm")
    assertThat(mySystemTraceTaskHandler.supportsDeviceAndProcess(mDevice, process)).isFalse()
  }

  @Test
  fun testGetTaskName() {
    assertThat(mySystemTraceTaskHandler.getTaskName()).isEqualTo("System Trace")
  }

  private fun mockDeviceInSystemTraceTaskHandler(device: Common.Device?, taskBasedUxEnabled: Boolean): SystemTraceTaskHandler {
    val profilersNow = spy(StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    ))
    val taskHomeTabModel = spy(profilersNow.taskHomeTabModel)
    MockitoKt.whenever(profilersNow.taskHomeTabModel).thenReturn(taskHomeTabModel)
    val sessionManagerNow = spy(profilersNow.sessionsManager)
    MockitoKt.whenever(sessionManagerNow.studioProfilers).thenReturn(profilersNow)
    MockitoKt.whenever(taskHomeTabModel.selectedDevice).thenReturn(
      device?.let { ProfilerDeviceSelection(device.model, 30, true, false, device) })
    return SystemTraceTaskHandler(sessionManagerNow, taskBasedUxEnabled);
  }

  private fun createDefaultPerfettoTraceConfiguration() = Trace.TraceConfiguration.newBuilder().setPerfettoOptions(
    PerfettoConfig.TraceConfig.getDefaultInstance()).build()

  private fun createFakeDevice(level: Int): Common.Device {
    val deviceName = "FakeUnitTestDevice";
    return Common.Device.newBuilder().setDeviceId(deviceName.hashCode().toLong())
      .setSerial(deviceName)
      .setState(Common.Device.State.ONLINE)
      .setFeatureLevel(level) // 28 is needed for perfetto
      .build()
  }

  private fun selectDevice(device: Common.Device) {
    myProfilers.taskHomeTabModel.processListModel.onDeviceSelection(device)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<ExposureLevel> {
      return listOf(ExposureLevel.DEBUGGABLE, ExposureLevel.PROFILEABLE)
    }
  }
}