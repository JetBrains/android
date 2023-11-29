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
package com.android.tools.profilers.taskbased.selections.recordings

import com.android.testutils.MockitoKt
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.selections.recordings.RecordingListModel
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import perfetto.protos.PerfettoConfig

class RecordingListModelTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("RecordingListModelTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var recordingListModel: RecordingListModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach {  myProfilers.addTaskHandler(it.key, it.value)  }
    recordingListModel = RecordingListModel(myProfilers, taskHandlers) {}
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Test
  fun `updated sessions trigger update in recording list model`() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Check the recording list to make sure the recordings/SessionItems are empty.
    var recordingList = recordingListModel.recordingList.value.toList()
    assertThat(recordingList.isEmpty()).isTrue()

    val session1Timestamp = 1L
    val session2Timestamp = 2L
    myTimer.currentTimeNs = session1Timestamp
    startAndStopSession(device, process1, Common.ProfilerTaskType.SYSTEM_TRACE)
    val session1 = myManager.selectedSession
    val heapDumpTimestamp = 10L
    val heapDumpInfo = Memory.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(session1Timestamp, session1Timestamp, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(session1.pid).build())
    myManager.update()

    // Check the recording list to make sure the recordings/SessionItems are updated.
    recordingList = recordingListModel.recordingList.value.toList()
    assertThat(recordingList.size).isEqualTo(1)
    assertThat(recordingList[0].containsExactlyOneArtifact()).isTrue()
    assertThat(recordingList[0].getChildArtifacts()[0]).isInstanceOf(HprofSessionArtifact::class.java)

    myTimer.currentTimeNs = session2Timestamp
    startAndStopSession(device, process2, Common.ProfilerTaskType.NATIVE_ALLOCATIONS)
    val session2 = myManager.selectedSession
    val cpuTraceTimestamp = 20L
    val cpuTraceInfo = Trace.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(
      cpuTraceTimestamp + 1).setConfiguration(
      Trace.TraceConfiguration.newBuilder().setPerfettoOptions(PerfettoConfig.TraceConfig.getDefaultInstance())).build()
    val cpuTrace = Common.Event.newBuilder()
      .setGroupId(session1Timestamp + cpuTraceTimestamp)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(session1Timestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                      .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
    myTransportService.addEventToStream(device.deviceId, cpuTrace.setPid(session2.pid).build())
    myManager.update()

    // Check the recording list to make sure the recordings/SessionItems were updated.
    recordingList = recordingListModel.recordingList.value.toList()
    assertThat(recordingList.size).isEqualTo(2)
    // New CPU Recording should be placed in the front of the list due to chronological sort of SessionItems.
    assertThat(recordingList[0].containsExactlyOneArtifact()).isTrue()
    assertThat(recordingList[0].getChildArtifacts()[0]).isInstanceOf(CpuCaptureSessionArtifact::class.java)
    val cpuCaptureSessionArtifact = recordingList[0].getChildArtifacts()[0] as CpuCaptureSessionArtifact
    assertThat(cpuCaptureSessionArtifact.artifactProto.hasConfiguration()).isTrue()
    assertThat(cpuCaptureSessionArtifact.artifactProto.configuration.hasPerfettoOptions()).isTrue()
    assertThat(recordingList[1].containsExactlyOneArtifact()).isTrue()
    assertThat(recordingList[1].getChildArtifacts()[0]).isInstanceOf(HprofSessionArtifact::class.java)
  }

  @Test
  fun `system trace task recording shows correct supported tasks`() {
    val sessionId = 1L
    val traceId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val systemTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
      myProfilers, session, sessionId, traceId,
      Trace.TraceConfiguration.newBuilder().setPerfettoOptions(PerfettoConfig.TraceConfig.getDefaultInstance()).build())
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(systemTraceArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("System Trace")
  }

  @Test
  fun `callstack sample task recording shows correct supported tasks`() {
    val sessionId = 1L
    val traceId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val callstackSampleArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
      myProfilers, session, sessionId, traceId,
      Trace.TraceConfiguration.newBuilder().setSimpleperfOptions(Trace.SimpleperfOptions.getDefaultInstance()).build())
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(callstackSampleArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Callstack Sample")
  }

  @Test
  fun `java kotlin method trace and sample task recording shows correct supported tasks`() {
    val sessionId = 1L
    val traceId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val javaKotlinMethodArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
      myProfilers, session, sessionId, traceId,
      Trace.TraceConfiguration.newBuilder().setArtOptions(Trace.ArtOptions.getDefaultInstance()).build())
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(javaKotlinMethodArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Java/Kotlin Method Trace, Java/Kotlin Method Sample (legacy)")
  }

  @Test
  fun `heap dump task recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val heapDumpArtifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(heapDumpArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Heap Dump")
  }

  @Test
  fun `native allocations task recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val nativeAllocationsArtifact = SessionArtifactUtils.createHeapProfdSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(nativeAllocationsArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Native Allocations")
  }

  @Test
  fun `java kotlin allocations task (non-legacy) recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val javaKotlinAllocationsArtifact = SessionArtifactUtils.createAllocationSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(javaKotlinAllocationsArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Java/Kotlin Allocations")
  }

  @Test
  fun `java kotlin allocations task (legacy) recording shows correct supported tasks`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val legacyJavaKotlinAllocationsArtifact = SessionArtifactUtils.createLegacyAllocationsSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(legacyJavaKotlinAllocationsArtifact))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Java/Kotlin Allocations")
  }

  @Test
  fun `recording with no artifacts shows live view tasks available`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf())
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("Live View")
  }

  @Test
  fun `recording with invalid artifacts no task available`() {
    val sessionItem = MockitoKt.mock<SessionItem>()
    MockitoKt.whenever(sessionItem.getChildArtifacts()).thenReturn(listOf(MockitoKt.mock <SessionArtifact<Memory.AllocationsInfo>>()))
    val supportedTasksString = recordingListModel.createStringOfSupportedTasks(sessionItem)
    assertThat(supportedTasksString).isEqualTo("No tasks available")
  }

  @Test
  fun `recording with one artifact is exportable`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val artifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(artifact))
    recordingListModel.onRecordingSelection(sessionItem)
    assertThat(recordingListModel.isSelectedRecordingExportable()).isTrue()
  }

  @Test
  fun `recording with zero artifacts is not exportable`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf())
    recordingListModel.onRecordingSelection(sessionItem)
    assertThat(recordingListModel.isSelectedRecordingExportable()).isFalse()
  }

  @Test
  fun `recording with more than one artifact is not exportable`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val artifact1 = SessionArtifactUtils.createHprofSessionArtifact(myProfilers, session, 0L, 1L)
    val artifact2 = SessionArtifactUtils.createHprofSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(artifact1, artifact2))
    recordingListModel.onRecordingSelection(sessionItem)
    assertThat(recordingListModel.isSelectedRecordingExportable()).isFalse()
  }

  @Test
  fun `recording with one, ongoing artifact is not exportable`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val artifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers, session, 0L, Long.MAX_VALUE)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(artifact))
    recordingListModel.onRecordingSelection(sessionItem)
    assertThat(recordingListModel.isSelectedRecordingExportable()).isFalse()
  }

  @Test
  fun `recording with one non-exportable artifact is not exportable`() {
    val sessionId = 1L
    val session = Common.Session.newBuilder().setSessionId(sessionId).build()
    val javaKotlinAllocationsArtifact = SessionArtifactUtils.createAllocationSessionArtifact(myProfilers, session, 0L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, sessionId, listOf(javaKotlinAllocationsArtifact))
    recordingListModel.onRecordingSelection(sessionItem)
    assertThat(recordingListModel.isSelectedRecordingExportable()).isFalse()
  }

  @Test
  fun `ongoing recording does not show up in recording list`() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    // Check the recording list to make sure the recordings/SessionItems are empty.
    var recordingList = recordingListModel.recordingList.value.toList()
    assertThat(recordingList.isEmpty()).isTrue()

    val sessionTimestamp = 1L
    myTimer.currentTimeNs = sessionTimestamp
    // Start a session but do not end it, simulating an ongoing task.
    startSession(device, process, Common.ProfilerTaskType.SYSTEM_TRACE)
    val session1 = myManager.selectedSession
    val heapDumpInfo = Memory.HeapDumpInfo.newBuilder().setStartTime(10L).setEndTime(Long.MAX_VALUE).build()
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(sessionTimestamp, sessionTimestamp, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(session1.pid).build())
    myManager.update()

    // Because the session/task is still ongoing, it should be filtered out of the recording list.
    recordingList = recordingListModel.recordingList.value.toList()
    assertThat(recordingList.size).isEqualTo(0)
  }

  private fun startAndStopSession(device: Common.Device, process: Common.Process, taskType: Common.ProfilerTaskType) {
    startSession(device, process, taskType)
    myManager.endCurrentSession()
    myManager.update()
  }

  private fun startSession(device: Common.Device, process: Common.Process, taskType: Common.ProfilerTaskType) {
    myManager.beginSession(1, device, process, taskType)
    myManager.update()
  }
}