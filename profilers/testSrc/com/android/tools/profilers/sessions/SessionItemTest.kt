/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.LiveStage
import com.android.tools.profilers.NullMonitorStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioMonitorStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.Utils.debuggableProcess
import com.android.tools.profilers.Utils.newProcess
import com.android.tools.profilers.Utils.onlineDevice
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.LiveTaskHandler
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import perfetto.protos.PerfettoConfig
import java.util.concurrent.TimeUnit

class SessionItemTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("SessionItemTestChannel", myTransportService)

  private val myIdeServices = FakeIdeProfilerServices()
  private val myProfilers by lazy { StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer) }

  @Before
  fun setup() {
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myProfilers.sessionsManager)
    taskHandlers.forEach{ (type, handler)  -> myProfilers.addTaskHandler(type, handler) }
  }

  @Test
  fun testNavigateToLiveStageWhenTaskBasedUxEnabled() {
    myIdeServices.enableTaskBasedUx(true)
    // Bypass error that selected task does not have corresponding task handler by adding a task handler for UNSPECIFIED task type.
    myProfilers.addTaskHandler(ProfilerTaskType.UNSPECIFIED, LiveTaskHandler(myProfilers.sessionsManager))

    val device = onlineDevice { deviceId = NEW_DEVICE_ID }
    val process = debuggableProcess { deviceId = NEW_DEVICE_ID; pid = NEW_PROCESS_ID }
    startSession(device, process)

    myProfilers.stage = MainMemoryProfilerStage(myProfilers)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)
    val sessionItem = myProfilers.sessionsManager.sessionArtifacts[0] as SessionItem
    // In TaskBasedUx, `sessionItem.onSelect()` is invoked after selecting a live view past recording and clicking on
    // 'Open profiler task'.
    sessionItem.onSelect()
    Truth.assertThat(myProfilers.stageClass).isEqualTo(LiveStage::class.java)
  }

  @Test
  fun testNavigateToLiveStageWhenTaskBasedUxEnabledAlreadyLiveStage() {
    myIdeServices.enableTaskBasedUx(true)
    // Bypass error that selected task does not have corresponding task handler by adding a task handler for UNSPECIFIED task type.
    myProfilers.addTaskHandler(ProfilerTaskType.UNSPECIFIED, LiveTaskHandler(myProfilers.sessionsManager))

    val device = onlineDevice { deviceId = NEW_DEVICE_ID }
    val process = debuggableProcess { deviceId = NEW_DEVICE_ID; pid = NEW_PROCESS_ID }
    startSession(device, process)
    myProfilers.stage = LiveStage(myProfilers)

    Truth.assertThat(myProfilers.stageClass).isEqualTo(LiveStage::class.java)
    val sessionItem = myProfilers.sessionsManager.sessionArtifacts[0] as SessionItem
    // In TaskBasedUx, `sessionItem.onSelect()` is invoked after selecting a live view past recording and clicking on
    // 'Open profiler task'.
    sessionItem.onSelect()
    Truth.assertThat(myProfilers.stageClass).isEqualTo(LiveStage::class.java)
  }

  @Test
  fun testNavigateToStudioMonitorStage() {
    myIdeServices.enableTaskBasedUx(false)

    Truth.assertThat(myProfilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = myProfilers.sessionsManager
    val device = onlineDevice { deviceId = NEW_DEVICE_ID }
    val process = newProcess { deviceId = NEW_DEVICE_ID; pid = 10 }
    sessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(StudioMonitorStage::class.java)

    // Navigate to a random stage, and make sure selecting the session item will go back to StudioMonitorStage.
    myProfilers.stage = MainMemoryProfilerStage(myProfilers)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    Truth.assertThat(myProfilers.stageClass).isEqualTo(StudioMonitorStage::class.java)
  }

  @Test
  fun testAvoidRedundantNavigationToMonitorStage() {
    myIdeServices.enableTaskBasedUx(false)

    Truth.assertThat(myProfilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = myProfilers.sessionsManager
    val device = onlineDevice { deviceId = NEW_DEVICE_ID }
    val process = newProcess { deviceId = NEW_DEVICE_ID; pid = 10 }
    sessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(StudioMonitorStage::class.java)

    val stage = myProfilers.stage
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    Truth.assertThat(myProfilers.stage).isEqualTo(stage)
  }

  @Test
  fun testNonFullSessionNavigation() {
    myIdeServices.enableTaskBasedUx(false)

    Truth.assertThat(myProfilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    generateMemoryCaptureEvents()
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)
    val sessionsManager = myProfilers.sessionsManager
    Truth.assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    // Selecting a memory capture session should not navigate to StudioMonitorStage
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testImportedHprofSessionName() {
    val device = onlineDevice { deviceId = NEW_DEVICE_ID }
    val process = debuggableProcess { deviceId = NEW_DEVICE_ID; pid = NEW_PROCESS_ID }
    generateMemoryCaptureEvents()
    val sessionsManager = myProfilers.sessionsManager
    Truth.assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    Truth.assertThat(sessionItem.getSubtitle()).isEqualTo(SessionItem.SESSION_LOADING)

    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(10).setEndTime(Long.MAX_VALUE).build()
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(heapDumpInfo.startTime, heapDumpInfo.startTime, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(process.pid).build())
    sessionsManager.update()
    Truth.assertThat(sessionItem.getSubtitle()).isEqualTo("Heap Dump")
  }

  @Test
  fun testDurationUpdates() {
    var aspectChangeCount1 = 0
    val observer1 = AspectObserver()
    val finishedSession = Common.Session.newBuilder()
      .setStartTimestamp(TimeUnit.SECONDS.toNanos(5))
      .setEndTimestamp(TimeUnit.SECONDS.toNanos(10)).build()
    val finishedSessionItem = SessionItem(myProfilers, finishedSession,
                                          Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    finishedSessionItem.addDependency(observer1)
      .onChange(SessionItem.Aspect.MODEL) { aspectChangeCount1++ }
    Truth.assertThat(finishedSessionItem.getSubtitle()).isEqualTo("5 sec")
    Truth.assertThat(aspectChangeCount1).isEqualTo(0)
    // Updating should not affect finished sessions.
    finishedSessionItem.update(TimeUnit.SECONDS.toNanos(1))
    Truth.assertThat(finishedSessionItem.getSubtitle()).isEqualTo("5 sec")
    Truth.assertThat(aspectChangeCount1).isEqualTo(0)

    var aspectChangeCount2 = 0
    val observer2 = AspectObserver()
    val ongoingSession = Common.Session.newBuilder()
      .setStartTimestamp(TimeUnit.SECONDS.toNanos(5))
      .setEndTimestamp(Long.MAX_VALUE).build()
    val ongoingSessionItem = SessionItem(myProfilers, ongoingSession,
                                         Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    ongoingSessionItem.addDependency(observer2)
      .onChange(SessionItem.Aspect.MODEL) { aspectChangeCount2++ }
    Truth.assertThat(ongoingSessionItem.getSubtitle()).isEqualTo("0 sec")
    Truth.assertThat(aspectChangeCount2).isEqualTo(0)
    ongoingSessionItem.update(TimeUnit.SECONDS.toNanos(2))
    Truth.assertThat(ongoingSessionItem.getSubtitle()).isEqualTo("2 sec")
    Truth.assertThat(aspectChangeCount2).isEqualTo(1)
  }

  @Test
  fun `get session name with valid metadata`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app (Pixel 3A XL)"
      type = Common.SessionMetaData.SessionType.FULL
    }.build())

    Truth.assertThat(sessionItem.name).isEqualTo("app (Pixel 3A XL)")
  }

  @Test
  fun `get session name uses raw metadata name when parsing fails`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app"  // the name should have the device name at the end, therefore this is invalid
      type = Common.SessionMetaData.SessionType.FULL
    }.build())

    Truth.assertThat(sessionItem.name).isEqualTo("com.google.app")
  }

  @Test
  fun `get session name uses raw metadata when session type is not FULL`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app (Pixel 3A XL)"
      type = Common.SessionMetaData.SessionType.UNSPECIFIED
    }.build())

    Truth.assertThat(sessionItem.name).isEqualTo("com.google.app (Pixel 3A XL)")
  }

  @Test
  fun `session with no child artifacts is not exportable`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app (Pixel 3A XL)"
      type = Common.SessionMetaData.SessionType.MEMORY_CAPTURE
    }.build())

    // Make sure there are no child artifacts.
    Truth.assertThat(sessionItem.getChildArtifacts()).isEmpty()
    // Despite being a valid session type (MEMORY_CAPTURE), no child artifacts will lead to canExport being false.
    Truth.assertThat(sessionItem.canExport).isFalse()
  }

  @Test
  fun `session with a single child artifacts is exportable`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app (Pixel 3A XL)"
      type = Common.SessionMetaData.SessionType.MEMORY_CAPTURE
    }.build())

    sessionItem.setChildArtifacts(listOf(
      CpuCaptureSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), Common.SessionMetaData.getDefaultInstance(),
                                Trace.TraceInfo.getDefaultInstance())))
    // The session item has a single child artifact, thus canExport should be true.
    Truth.assertThat(sessionItem.canExport).isTrue()
  }

  @Test
  fun `system trace task recording shows correct supported tasks`() {
    val sessionId = 1L
    val traceId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val systemTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
      myProfilers, session, sessionId, traceId,
      Trace.TraceConfiguration.newBuilder().setPerfettoOptions(PerfettoConfig.TraceConfig.getDefaultInstance()).build())
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.SYSTEM_TRACE,
                                                             listOf(systemTraceArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `callstack sample task recording shows correct supported tasks`() {
    val sessionId = 1L
    val traceId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val callstackSampleArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
      myProfilers, session, sessionId, traceId,
      Trace.TraceConfiguration.newBuilder().setSimpleperfOptions(Trace.SimpleperfOptions.getDefaultInstance()).build())
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.CALLSTACK_SAMPLE,
                                                             listOf(callstackSampleArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.CALLSTACK_SAMPLE)
  }

  @Test
  fun `java kotlin method recording shows correct supported tasks`() {
    val sessionId = 1L
    val traceId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val javaKotlinMethodArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
      myProfilers, session, sessionId, traceId,
      Trace.TraceConfiguration.newBuilder().setArtOptions(Trace.ArtOptions.getDefaultInstance()).build())
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING,
                                                             listOf(javaKotlinMethodArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)
  }

  @Test
  fun `heap dump task recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val heapDumpArtifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.HEAP_DUMP,
                                                             listOf(heapDumpArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.HEAP_DUMP)
  }

  @Test
  fun `native allocations task recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val nativeAllocationsArtifact = SessionArtifactUtils.createHeapProfdSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.NATIVE_ALLOCATIONS,
                                                             listOf(nativeAllocationsArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.NATIVE_ALLOCATIONS)
  }

  @Test
  fun `java kotlin allocations task (non-legacy) recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val javaKotlinAllocationsArtifact = SessionArtifactUtils.createAllocationSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
                                                             listOf(javaKotlinAllocationsArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
  }

  @Test
  fun `java kotlin allocations task (legacy) recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val legacyJavaKotlinAllocationsArtifact = SessionArtifactUtils.createLegacyAllocationsSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
                                                             listOf(legacyJavaKotlinAllocationsArtifact))
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
  }

  @Test
  fun `recording with no artifacts shows live view tasks available`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.LIVE_VIEW, listOf())
    val supportedTask = sessionItem.getTaskType()
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.LIVE_VIEW)
  }

  @Test
  fun `mismatch in intended task and supported task`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    // The task was intended to be a system trace (indicated by the session metadata), but has no artifacts that would indicate it was a
    // system trace recording.
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, ProfilerTaskType.SYSTEM_TRACE, listOf())
    val supportedTask = sessionItem.getTaskType()
    // Because there is a mismatch in intended and actual task type, UNSPECIFIED task type should be returned.
    Truth.assertThat(supportedTask).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }

  private fun generateMemoryCaptureEvents() {
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionStartEvent(1, 2, 0,
                                                                                       Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE,
                                                                                       0).build())
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 2, 0).build())
    myProfilers.sessionsManager.update()
  }

  private fun startSession(device: Common.Device, process: Common.Process) {
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
  }

  companion object {
    const val NEW_DEVICE_ID = 1L
    const val NEW_PROCESS_ID = 2
  }
}