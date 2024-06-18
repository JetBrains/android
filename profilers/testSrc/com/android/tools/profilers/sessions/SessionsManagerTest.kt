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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Device
import com.android.tools.profiler.proto.Memory.AllocationsInfo
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilers.buildSessionName
import com.android.tools.profilers.Utils.debuggableProcess
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.AllocationSessionArtifact
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.SystemTraceTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.NativeAllocationsTaskHandler
import com.google.common.annotations.VisibleForTesting
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class SessionsManagerTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val myThrown = ExpectedException.none()
  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("SessionsManagerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myObserver: SessionsAspectObserver
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myObserver = SessionsAspectObserver()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    myManager.addDependency(myObserver)
      .onChange(SessionAspect.SELECTED_SESSION) { myObserver.selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { myObserver.profilingSessionChanged() }
      .onChange(SessionAspect.SESSIONS) { myObserver.sessionsChanged() }
      .onChange(SessionAspect.ONGOING_SESSION_NEWLY_ENDED) { myObserver.ongoingSessionEnded() }

    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test(expected = AssertionError::class)
  fun testBeginSessionWithOfflineDevice() {
    val offlineDevice = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.DISCONNECTED).build()
    val onlineProcess = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.DEAD).build()
    beginSessionHelper(offlineDevice, onlineProcess)
  }

  @Test(expected = AssertionError::class)
  fun testBeginSessionWithDeadProcess() {
    val onlineDevice = Common.Device.newBuilder().setDeviceId(2).setState(Common.Device.State.ONLINE).build()
    val offlineProcess = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.DEAD).build()
    beginSessionHelper(onlineDevice, offlineProcess)
  }

  @Test
  fun testBeginSession() {
    ideProfilerServices.enableTaskBasedUx(false)

    val streamId = 1L
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(streamId).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()
    beginSessionHelper(onlineDevice, onlineProcess)

    val session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    // The currentTaskType is updated with the task type from the event data received for a newly started session.
    // Here we check if the correct task type was populated with the UNSPECIFIED_TASK type as task type was not provided.
    val taskType = myManager.currentTaskType
    assertThat(taskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)

    val sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)
  }

  @Test
  fun testBeginAndEndSessionWithTask() {
    ideProfilerServices.enableTaskBasedUx(true)
    val streamId = 1L
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(streamId).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, SystemTraceTaskHandler(myManager, false))
    beginSessionWithTaskHelper(onlineDevice, onlineProcess, Common.ProfilerTaskType.SYSTEM_TRACE)

    val session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    // The currentTaskType is updated with the task type from the event data received for a newly started session.
    // Here we check if the correct task type was populated.
    val taskType = myManager.currentTaskType
    assertThat(taskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    val sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)
    // The ongoing session has not been terminated, and thus the ONGOING_SESSION_NEWLY_ENDED aspect has not been fired yet.
    assertThat(myObserver.ongoingSessionEndedCount).isEqualTo(0)

    myManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // The ongoing session has been terminated, resulting in the firing of the ONGOING_SESSION_NEWLY_ENDED aspect.
    assertThat(myObserver.ongoingSessionEndedCount).isEqualTo(1)
  }

  @Test
  fun testValidSessionMetadataForDebuggableProcess() {
    ideProfilerServices.enableTaskBasedUx(false)

    val streamId = 1L
    val processId = 10
    val device = Common.Device.newBuilder().apply {
      deviceId = streamId
      state = Common.Device.State.ONLINE
      featureLevel = AndroidVersion.VersionCodes.O
    }.build()
    val process = Common.Process.newBuilder().apply {
      pid = processId
      state = Common.Process.State.ALIVE
      abiCpuArch = "arm64"
      exposureLevel = Common.Process.ExposureLevel.DEBUGGABLE
    }.build()
    beginSessionHelper(device, process)

    val session = myManager.selectedSession
    val sessionMetadata = myManager.selectedSessionMetaData
    assertThat(sessionMetadata.sessionId).isEqualTo(session.sessionId)
    assertThat(sessionMetadata.sessionName).isEqualTo(buildSessionName(device, process))
    assertThat(sessionMetadata.type).isEqualTo(Common.SessionMetaData.SessionType.FULL)
    assertThat(sessionMetadata.processAbi).isEqualTo("arm64")
    assertThat(sessionMetadata.jvmtiEnabled).isTrue()
  }

  @Test
  fun testValidSessionMetadataForProfileableProcess() {
    ideProfilerServices.enableTaskBasedUx(false)

    val streamId = 1L
    val processId = 10
    val device = Common.Device.newBuilder().apply {
      deviceId = streamId
      state = Common.Device.State.ONLINE
      featureLevel = AndroidVersion.VersionCodes.Q
    }.build()
    val process = Common.Process.newBuilder().apply {
      pid = processId
      state = Common.Process.State.ALIVE
      abiCpuArch = "arm64"
      exposureLevel = Common.Process.ExposureLevel.PROFILEABLE
    }.build()
    beginSessionHelper(device, process)

    val session = myManager.selectedSession
    val sessionMetadata = myManager.selectedSessionMetaData
    assertThat(sessionMetadata.sessionId).isEqualTo(session.sessionId)
    assertThat(sessionMetadata.sessionName).isEqualTo(buildSessionName(device, process))
    assertThat(sessionMetadata.type).isEqualTo(Common.SessionMetaData.SessionType.FULL)
    assertThat(sessionMetadata.processAbi).isEqualTo("arm64")
    assertThat(sessionMetadata.jvmtiEnabled).isFalse()
  }

  @Test
  fun testBeginSessionCannotRunTwice() {
    ideProfilerServices.enableTaskBasedUx(false)

    val deviceId = 1
    val pid1 = 10
    val pid2 = 20
    val onlineDevice = Common.Device.newBuilder().setDeviceId(deviceId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess1 = Common.Process.newBuilder().setPid(pid1).setState(Common.Process.State.ALIVE).build()
    val onlineProcess2 = Common.Process.newBuilder().setPid(pid2).setState(Common.Process.State.ALIVE).build()
    beginSessionHelper(onlineDevice, onlineProcess1)

    myThrown.expect(AssertionError::class.java)
    beginSessionHelper(onlineDevice, onlineProcess2)
  }

  @Test
  fun testEndSession() {
    ideProfilerServices.enableTaskBasedUx(false)

    val streamId = 1
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(streamId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()

    // endSession calls on no active session is a no-op
    myManager.endCurrentSession()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(0)

    beginSessionHelper(onlineDevice, onlineProcess)
    var session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    var sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)

    endSessionHelper()
    session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.isSessionAlive).isFalse()

    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(2)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(2)
  }

  @Test
  fun testSetInvalidSession() {
    val session = Common.Session.newBuilder().setSessionId(1).build()
    myThrown.expect(AssertionError::class.java)
    myManager.setSession(session)
  }

  @Test
  fun testSetSession() {
    ideProfilerServices.enableTaskBasedUx(false)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    val session2 = myManager.selectedSession
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(3)

    myManager.setSession(session1)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(4)

    myManager.setSession(session2)
    assertThat(myManager.selectedSession).isEqualTo(session2)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(5)

    myManager.setSession(Common.Session.getDefaultInstance())
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(6)
  }

  @Test
  fun testSetSessionById() {
    ideProfilerServices.enableTaskBasedUx(false)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    val session2 = myManager.selectedSession

    assertThat(myManager.setSessionById(0)).isFalse()
    assertThat(myManager.selectedSession).isEqualTo(session2)

    assertThat(myManager.setSessionById(session1.sessionId)).isTrue()
    assertThat(myManager.selectedSession).isEqualTo(session1)

    assertThat(myManager.setSessionById(session2.sessionId)).isTrue()
    assertThat(myManager.selectedSession).isEqualTo(session2)
  }

  @Test
  fun testSetSessionStopsAutoProfiling() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    myProfilers.autoProfilingEnabled = true

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    assertThat(myProfilers.autoProfilingEnabled).isTrue()

    myManager.setSession(session1)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myProfilers.autoProfilingEnabled).isFalse()
  }

  @Test
  fun testSwitchingNonProfilingSession() {
    ideProfilerServices.enableTaskBasedUx(false)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    val session2 = myManager.selectedSession

    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(3)
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.isSessionAlive).isTrue()

    // Explicitly set to a different session should not change the profiling session.
    myManager.setSession(session1)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(4)
  }

  @Test
  fun testEndSessionIsNotAlive() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val session1Timestamp = 1L
    myTimer.currentTimeNs = session1Timestamp
    beginSessionHelper(device, process1)
    endSessionHelper()
    assertThat(SessionsManager.isSessionAlive(myManager.profilingSession)).isFalse()
  }

  /**
   * Note: This test does not use the global manager because it needs to set the native memory sampling flag to enabled.
   */
  @Test
  fun testNativeHeapArtifacts() {
    ideProfilerServices.enableTaskBasedUx(false)

    val profiler = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    val manager = profiler.sessionsManager
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()

    val session1Timestamp = 1L
    myTimer.currentTimeNs = session1Timestamp
    manager.beginSession(1, device, process1)
    manager.update()
    manager.endCurrentSession()
    manager.update()
    val session1 = manager.selectedSession

    val nativeHeapTimestamp = 30L
    val nativeHeapInfo = Trace.TraceData.newBuilder().setTraceStarted(Trace.TraceData.TraceStarted.newBuilder().setTraceInfo(
      Trace.TraceInfo.newBuilder().setFromTimestamp(nativeHeapTimestamp).setToTimestamp(
        nativeHeapTimestamp + 1))).build()
    val nativeHeapData = ProfilersTestData.generateMemoryTraceData(nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo)
    myTransportService.addEventToStream(device.deviceId, nativeHeapData.setPid(session1.pid).build())
    manager.update()

    // The Hprof and CPU capture artifacts are now included and sorted in ascending order
    val sessionItems = manager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    assertThat(sessionItems[0]).isInstanceOf(SessionItem::class.java)
    assertThat(sessionItems[1]).isInstanceOf(HeapProfdSessionArtifact::class.java)
  }

  @Test
  fun testSessionArtifactsUpToDate() {
    ideProfilerServices.enableTaskBasedUx(false)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    val session1Timestamp = 1L
    val session2Timestamp = 2L
    myTimer.currentTimeNs = session1Timestamp
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    myTimer.currentTimeNs = session2Timestamp
    beginSessionHelper(device, process2)
    endSessionHelper()
    val session2 = myManager.selectedSession

    var sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionItems[0] as SessionItem
    var sessionItem1 = sessionItems[1] as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.getChildArtifacts()).isEmpty()
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.getChildArtifacts()).isEmpty()

    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val legacyAllocationsInfoTimestamp = 30L
    val liveAllocationsInfoTimestamp = 40L
    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = Trace.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    val legacyAllocationsInfo = AllocationsInfo.newBuilder()
      .setStartTime(legacyAllocationsInfoTimestamp).setEndTime(legacyAllocationsInfoTimestamp + 1).setLegacy(true).build()
    val liveAllocationsInfo = AllocationsInfo.newBuilder().setStartTime(liveAllocationsInfoTimestamp)
      .setEndTime(liveAllocationsInfoTimestamp + 1).build()

    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(session1Timestamp, session1Timestamp, heapDumpInfo)
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(session1.pid).build())
    myTransportService.addEventToStream(device.deviceId, heapDumpEvent.setPid(session2.pid).build())

    myTransportService.addEventToStream(
      device.deviceId,
      ProfilersTestData.generateMemoryAllocationInfoData(legacyAllocationsInfoTimestamp, session1.pid, legacyAllocationsInfo).build())
    myTransportService.addEventToStream(
      device.deviceId,
      ProfilersTestData.generateMemoryAllocationInfoData(liveAllocationsInfoTimestamp, session1.pid, liveAllocationsInfo).build())
    myTransportService.addEventToStream(
      device.deviceId,
      ProfilersTestData.generateMemoryAllocationInfoData(legacyAllocationsInfoTimestamp, session2.pid, legacyAllocationsInfo).build())
    myTransportService.addEventToStream(
      device.deviceId,
      ProfilersTestData.generateMemoryAllocationInfoData(liveAllocationsInfoTimestamp, session2.pid, liveAllocationsInfo).build())

    val cpuTrace = Common.Event.newBuilder()
      .setGroupId(session1Timestamp + cpuTraceTimestamp)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(session1Timestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
    myTransportService.addEventToStream(device.deviceId, cpuTrace.setPid(session1.pid).build())
    myTransportService.addEventToStream(device.deviceId, cpuTrace.setPid(session2.pid).build())

    myManager.update()

    // The Hprof and CPU capture artifacts are now included and sorted in ascending order
    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(10)
    sessionItem0 = sessionItems[0] as SessionItem
    val liveAllocationsItem0 = sessionItems[1] as AllocationSessionArtifact
    val legacyAllocationsItem0 = sessionItems[2] as LegacyAllocationsSessionArtifact
    val cpuCaptureItem0 = sessionItems[3] as CpuCaptureSessionArtifact
    val hprofItem0 = sessionItems[4] as HprofSessionArtifact
    sessionItem1 = sessionItems[5] as SessionItem
    val liveAllocationsItem1 = sessionItems[6] as AllocationSessionArtifact
    val legacyAllocationsItem1 = sessionItems[7] as LegacyAllocationsSessionArtifact
    val cpuCaptureItem1 = sessionItems[8] as CpuCaptureSessionArtifact
    val hprofItem1 = sessionItems[9] as HprofSessionArtifact

    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.getChildArtifacts()).containsExactly(liveAllocationsItem0, legacyAllocationsItem0, cpuCaptureItem0, hprofItem0)
    assertThat(hprofItem0.session).isEqualTo(session2)
    assertThat(hprofItem0.timestampNs).isEqualTo(heapDumpTimestamp - session2Timestamp)
    assertThat(cpuCaptureItem0.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.timestampNs).isEqualTo(cpuTraceTimestamp - session2Timestamp)
    assertThat(legacyAllocationsItem0.session).isEqualTo(session2)
    assertThat(legacyAllocationsItem0.timestampNs).isEqualTo(legacyAllocationsInfoTimestamp - session2Timestamp)
    assertThat(liveAllocationsItem0.session).isEqualTo(session2)
    assertThat(liveAllocationsItem0.timestampNs).isEqualTo(liveAllocationsInfoTimestamp - session2Timestamp)
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.getChildArtifacts()).containsExactly(liveAllocationsItem1, legacyAllocationsItem1, cpuCaptureItem1, hprofItem1)
    assertThat(hprofItem1.session).isEqualTo(session1)
    assertThat(hprofItem1.timestampNs).isEqualTo(heapDumpTimestamp - session1Timestamp)
    assertThat(cpuCaptureItem1.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.timestampNs).isEqualTo(cpuTraceTimestamp - session1Timestamp)
    assertThat(legacyAllocationsItem1.session).isEqualTo(session1)
    assertThat(legacyAllocationsItem1.timestampNs).isEqualTo(legacyAllocationsInfoTimestamp - session1Timestamp)
    assertThat(liveAllocationsItem1.session).isEqualTo(session1)
    assertThat(liveAllocationsItem1.timestampNs).isEqualTo(liveAllocationsInfoTimestamp - session1Timestamp)
  }

  @Test
  fun testImportedSessionOnlyProcessedWhenEnded() {
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionStartEvent(1, 1, 1,
                                                                                       Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE,
                                                                                       1).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(0)

    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 1, 2).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
  }

  // The epoch time in session meta data is the time when the file is imported.
  @Test
  fun testImportedSessionIsSelectedByImportTime() {
    ideProfilerServices.enableTaskBasedUx(false)

    // Note the event may be added to the pipeline out of order (of epoch time).
    myTransportService.addEventToStream(
      1, ProfilersTestData.generateSessionStartEvent(
      1, 1, 1, Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE, 200).build())
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 1, 2).build())
    myTransportService.addEventToStream(
      3, ProfilersTestData.generateSessionStartEvent(
      3, 3, 3, Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE, 100).build())
    myTransportService.addEventToStream(3, ProfilersTestData.generateSessionEndEvent(3, 3, 4).build())
    myManager.update()
    assertThat(myManager.selectedSession.sessionId).isEqualTo(1)
  }

  @Test
  fun testImportedSessionDoesNotHaveChildren() {
    val session1Timestamp = 1L
    val session2Timestamp = 2L

    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build()
    val cpuTraceInfo = Trace.TraceInfo.newBuilder()
      .setConfiguration(Trace.TraceConfiguration.newBuilder()).build()

    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionStartEvent(1, 1, session1Timestamp,
                                                                                       Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE,
                                                                                       1).build())
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 1, session1Timestamp).build())
    val heapDumpEvent = ProfilersTestData.generateMemoryHeapDumpData(session1Timestamp, session1Timestamp, heapDumpInfo)
    myTransportService.addEventToStream(1, heapDumpEvent.build())

    myTransportService.addEventToStream(2, ProfilersTestData.generateSessionStartEvent(2, 2, session2Timestamp,
                                                                                       Common.SessionData.SessionStarted.SessionType.CPU_CAPTURE,
                                                                                       2).build())
    myTransportService.addEventToStream(2, ProfilersTestData.generateSessionEndEvent(2, 2, session2Timestamp).build())
    val cpuTrace = Common.Event.newBuilder()
      .setGroupId(session2Timestamp)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(session2Timestamp)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
      .build()
    myTransportService.addEventToStream(2, cpuTrace)

    myManager.update()

    assertThat(myManager.sessionArtifacts.size).isEqualTo(2)
    val cpuTraceSessionItem = myManager.sessionArtifacts[0] as SessionItem
    assertThat(cpuTraceSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.CPU_CAPTURE)
    val hprofSessionItem = myManager.sessionArtifacts[1] as SessionItem
    assertThat(hprofSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
  }

  @Test
  fun testImportedSessionAutoSelectedWithTaskBasedUXDisabled() {
    (myProfilers.ideServices as FakeIdeProfilerServices).enableTaskBasedUx(false)
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionStartEvent(1, 1, 1,
                                                                                       Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE,
                                                                                       1).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(0)

    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 1, 2).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    // The imported session's id is 1, so we expect that to be the selected session's id.
    assertThat(myManager.selectedSession.sessionId).isEqualTo(1)
  }

  @Test
  fun testImportedSessionNotAutoSelectedWithTaskBasedUXEnabled() {
    (myProfilers.ideServices as FakeIdeProfilerServices).enableTaskBasedUx(true)
    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionStartEvent(1, 1, 1,
                                                                                       Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE,
                                                                                       1).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(0)

    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 1, 2).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    // The imported session's id is 1, so we do not expect that to be the selected session's id.
    assertThat(myManager.selectedSession.sessionId).isEqualTo(0)
  }

  @Test
  fun testImportedSessionDoesNotTriggerSessionNewlyEndedAspectWithTaskBasedUXEnabled() {
    ideProfilerServices.enableTaskBasedUx(true)
    myTransportService.addEventToStream(
      1, ProfilersTestData.generateSessionStartEvent(1, 1, 1, Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE, 1).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(0)

    myTransportService.addEventToStream(1, ProfilersTestData.generateSessionEndEvent(1, 1, 2).build())
    myManager.update()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    // The imported session's id is 1, so we do not expect that to be the selected session's id.
    assertThat(myManager.selectedSession.sessionId).isEqualTo(0)
    // Aspect should not have been triggered at all with imported session.
    assertThat(myObserver.ongoingSessionEndedCount).isEqualTo(0)
  }

  @Test
  fun testSessionsAspectOnlyTriggeredWithChanges() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)

    beginSessionHelper(device, process1)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)

    // Triggering update with the same data should not fire the aspect.
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)

    val heapDumpTimestamp = 10L
    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    myTransportService.addEventToStream(device.deviceId,
                                        ProfilersTestData.generateMemoryHeapDumpData(heapDumpInfo.startTime, heapDumpInfo.startTime,
                                                                                     heapDumpInfo)
                                          .setPid(process1.pid)
                                          .build())
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)
    // Repeated update should not fire the aspect.
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)

    val cpuTraceTimestamp = 20L
    val cpuTraceInfo = Trace.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()

    myTransportService.addEventToStream(device.deviceId, Common.Event.newBuilder()
      .setGroupId(cpuTraceTimestamp)
      .setPid(process1.pid)
      .setKind(Common.Event.Kind.CPU_TRACE)
      .setTimestamp(myTimer.currentTimeNs)
      .setIsEnded(true)
      .setTraceData(Trace.TraceData.newBuilder()
                     .setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(cpuTraceInfo).build()))
      .build())

    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    // Repeated update should not fire the aspect.
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
  }

  @Test
  fun testDeleteProfilingSession() {
    ideProfilerServices.enableTaskBasedUx(false)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = debuggableProcess { pid = 10; deviceId = 1 }
    val process2 = debuggableProcess { pid = 20; deviceId = 1 }
    val process3 = debuggableProcess { pid = 30; deviceId = 1 }
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process1)
    myTransportService.addProcess(device, process2)
    myTransportService.addProcess(device, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process1)

    // Create a finished session and a ongoing profiling session.
    endSessionHelper()
    myProfilers.setProcess(device, process2)
    myManager.update()
    val session1 = myManager.sessionArtifacts[1].session
    val session2 = myManager.selectedSession

    // Selects the first session so the profiling session is unselected, then delete the profiling session
    myManager.setSession(session1)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myManager.isSessionAlive).isFalse()

    myManager.deleteSession(session2)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    assertThat(myManager.sessionArtifacts[0].session).isEqualTo(session1)

    // Begin another profiling session and delete it while it is still selected
    myProfilers.setProcess(device, process3)
    myManager.update()
    val session3 = myManager.selectedSession
    assertThat(myManager.profilingSession).isEqualTo(session3)
    assertThat(myManager.selectedSession).isEqualTo(session3)
    assertThat(myManager.isSessionAlive).isTrue()

    myManager.deleteSession(session3)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    assertThat(myManager.sessionArtifacts[0].session).isEqualTo(session1)
  }

  @Test
  fun testGetAllSessions() {
    val device1 = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setDeviceId(1).setPid(10).setState(Common.Process.State.ALIVE).build()
    val device2 = Common.Device.newBuilder().setDeviceId(2).setState(Common.Device.State.ONLINE).build()
    val process2 = Common.Process.newBuilder().setDeviceId(2).setPid(2).setState(Common.Process.State.ALIVE).build()
    myTransportService.addDevice(device2)
    myTransportService.addProcess(device2, process2)
    // Create session for device/process 2
    myManager.beginSession(2, device2, process2)
    myManager.endCurrentSession()
    myTransportService.addDevice(device1)
    myTransportService.addProcess(device1, process1)
    // Create session for device/process 1
    myManager.beginSession(1, device1, process1)
    myManager.endCurrentSession()
    // Because we haven't updated we should have 0 session data.
    assertThat(myManager.sessionArtifacts).hasSize(0)
    // Trigger an update to get both sesssions from both streams.
    myManager.update()
    assertThat(myManager.sessionArtifacts).hasSize(2)

  }

  @Test
  fun testDeleteUnselectedSession() {
    ideProfilerServices.enableTaskBasedUx(false)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = debuggableProcess { pid = 10; deviceId = 1 }
    val process2 = debuggableProcess { pid = 20; deviceId = 1 }
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process1)
    myTransportService.addProcess(device, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process1)
    // Create a finished session and a ongoing profiling session.
    endSessionHelper()
    myProfilers.setProcess(device, process2)
    myManager.update()
    val session1 = myManager.sessionArtifacts[1].session
    val session2 = myManager.selectedSession
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.selectedSession).isEqualTo(session2)
    assertThat(myManager.isSessionAlive).isTrue()
    assertThat(myProfilers.device).isEqualTo(device)
    assertThat(myProfilers.process).isEqualTo(process2)

    myManager.deleteSession(session1)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.selectedSession).isEqualTo(session2)
    assertThat(myManager.isSessionAlive).isTrue()
    assertThat(myProfilers.device).isEqualTo(device)
    assertThat(myProfilers.process).isEqualTo(process2)
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    assertThat(myManager.sessionArtifacts[0].session).isEqualTo(session2)
  }

  @Test
  fun testDeleteSelectedSessionInTaskBasedUX() {
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, SystemTraceTaskHandler(myManager, false))
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = debuggableProcess { pid = 10; deviceId = 1 }
    myTransportService.addDevice(device)
    myTransportService.addProcess(device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process, Common.ProfilerTaskType.SYSTEM_TRACE, false)
    myManager.update()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Create a finished session and a ongoing profiling session.
    endSessionHelper()
    myManager.update()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    val session = myManager.selectedSession
    assertThat(myManager.selectedSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myProfilers.device).isEqualTo(device)
    assertThat(myProfilers.process).isEqualTo(process)

    myManager.deleteSession(session)
    myManager.update()
    // Although deleted, the session remains selected.
    assertThat(myManager.selectedSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myProfilers.device).isEqualTo(device)
    assertThat(myProfilers.process).isEqualTo(process)
    assertThat(myManager.sessionArtifacts.size).isEqualTo(0)
  }

  @Test
  fun `new ongoing FULL session with non-UNSPECIFIED task type is auto-selected as profiling and selected session in task-based ux`() {
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, SystemTraceTaskHandler(myManager, false))
    val sessionId = 123L
    val streamId = 1L
    val startTimestamp = 1L
    val pid = 456
    // If the Task-Based UX is disabled, any new session (ongoing or complete) is enough to auto-select the session as the profiling
    // and selected session. Under the Task-Based UX, only if the user has begun a new session that has not complete (a new, ongoing task)
    // with a non-UNSPECIFIED task type, then it will be auto-selected as the profiling and selected session.
    myTransportService.addEventToStream(streamId,
                                        ProfilersTestData.generateSessionStartEvent(streamId, sessionId, startTimestamp,
                                                                                    Common.SessionData.SessionStarted.SessionType.FULL,
                                                                                    Common.ProfilerTaskType.SYSTEM_TRACE,
                                                                                    startTimestamp, pid).build())
    myManager.update()
    assertThat(myManager.profilingSession.sessionId).isEqualTo(123)
    assertThat(myManager.selectedSession.sessionId).isEqualTo(123)
  }

  @Test
  fun `new ongoing FULL session with UNSPECIFIED task type is not auto-selected as profiling and selected session in task-based ux`() {
    ideProfilerServices.enableTaskBasedUx(true)
    val sessionId = 123L
    val streamId = 1L
    val startTimestamp = 1L
    val pid = 456
    // If the Task-Based UX is disabled, any new session (ongoing or complete) is enough to auto-select the session as the profiling
    // and selected session. Under the Task-Based UX, only if the user has begun a new session that has not complete (a new, ongoing task)
    // with a non-UNSPECIFIED task type set as the SessionsManger.currentTaskType, then it will be auto-selected as the profiling and
    // selected session.
    myTransportService.addEventToStream(streamId,
                                        ProfilersTestData.generateSessionStartEvent(streamId, sessionId, startTimestamp,
                                                                                    Common.SessionData.SessionStarted.SessionType.FULL,
                                                                                    Common.ProfilerTaskType.UNSPECIFIED_TASK,
                                                                                    startTimestamp, pid).build())
    myManager.update()
    assertThat(myManager.profilingSession.sessionId).isNotEqualTo(123)
    assertThat(myManager.selectedSession.sessionId).isNotEqualTo(123)
  }

  @Test
  fun `newly complete FULL session with UNSPECIFIED task type is not auto-selected in task-based ux`() {
    ideProfilerServices.enableTaskBasedUx(true)
    val sessionId = 123L
    val streamId = 1L
    val startTimestamp = 1L
    val endTimestamp = 2L
    val pid = 456;
    // If the Task-Based UX is disabled, a new, completed FULL session is enough to auto-select the session as the selected session and
    // profiling session. Under the Task-Based UX, complete sessions must be explicitly selected, and thus the auto-selection in
    // SessionsManger#update is prevented except if the user explicitly selects a previously completed session (completed full session with
    // a non-UNSPECIFIED set as the SessionsManger.currentTaskType). The following session events would trigger a session auto-selection
    // without Task-Based UX enabled and are thus utilized to make sure that the auto-selection does not occur with Task-Based UX enabled.
    myTransportService.addEventToStream(streamId,
                                        ProfilersTestData.generateSessionStartEvent(streamId, sessionId, startTimestamp,
                                                                                    Common.SessionData.SessionStarted.SessionType.FULL,
                                                                                    Common.ProfilerTaskType.UNSPECIFIED_TASK,
                                                                                    startTimestamp, pid).build())
    myTransportService.addEventToStream(streamId, ProfilersTestData.generateSessionEndEvent(streamId, sessionId, endTimestamp).build())
    myManager.update()

    // Auto-selection should not have occurred (non-UNSPECIFIED task type set for SessionsManager.currentTaskType is required in Task-Based
    // UX for newly complete sessions to be selected).
    assertThat(myManager.profilingSession.sessionId).isNotEqualTo(123)
    assertThat(myManager.selectedSession.sessionId).isNotEqualTo(123)
  }

  @Test
  fun `newly complete FULL session with non-UNSPECIFIED task type is not auto-selected in task-based ux`() {
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, SystemTraceTaskHandler(myManager, false))
    val sessionId = 123L
    val streamId = 1L
    val startTimestamp = 1L
    val endTimestamp = 2L
    val pid = 456
    myTransportService.addEventToStream(streamId,
                                        ProfilersTestData.generateSessionStartEvent(streamId, sessionId, startTimestamp,
                                                                                    Common.SessionData.SessionStarted.SessionType.FULL,
                                                                                    Common.ProfilerTaskType.SYSTEM_TRACE,
                                                                                    startTimestamp, pid).build())
    myTransportService.addEventToStream(streamId, ProfilersTestData.generateSessionEndEvent(streamId, sessionId, endTimestamp).build())
    myManager.update()

    // Auto-selection should not have occurred (explicit session selection is required in Task-Based UX for complete sessions).
    assertThat(myManager.profilingSession.sessionId).isNotEqualTo(123)
    assertThat(myManager.selectedSession.sessionId).isNotEqualTo(123)
  }

  @Test
  fun `explicitly selecting a complete FULL session with non-UNSPECIFIED task type triggers session selection in task-based ux`() {
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers.addTaskHandler(ProfilerTaskType.NATIVE_ALLOCATIONS, NativeAllocationsTaskHandler(myManager))
    val sessionId = 123L
    val streamId = 1L
    val startTimestamp = 1L
    val endTimestamp = 2L
    val pid = 456
    myTransportService.addEventToStream(streamId,
                                        ProfilersTestData.generateSessionStartEvent(streamId, sessionId, startTimestamp,
                                                                                    Common.SessionData.SessionStarted.SessionType.FULL,
                                                                                    Common.ProfilerTaskType.NATIVE_ALLOCATIONS,
                                                                                    startTimestamp, pid).build())
    myTransportService.addEventToStream(streamId, ProfilersTestData.generateSessionEndEvent(streamId, sessionId, endTimestamp).build())
    // Populate the underlying session artifact so that createArgs successfully construct an artifact to load. Otherwise, an
    // IllegalStateException will be raised.
    val nativeHeapTimestamp = 30L
    val nativeHeapInfo = Trace.TraceData.newBuilder().setTraceStarted(Trace.TraceData.TraceStarted.newBuilder().setTraceInfo(
      Trace.TraceInfo.newBuilder().setFromTimestamp(nativeHeapTimestamp).setToTimestamp(
        nativeHeapTimestamp + 1))).build()
    val nativeHeapData = ProfilersTestData.generateMemoryTraceData(nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo)
    myTransportService.addEventToStream(streamId, nativeHeapData.setPid(pid).build())
    myManager.update()

    // Auto-selection should not have occurred (explicit session selection is required in Task-Based UX for complete sessions).
    assertThat(myManager.profilingSession.sessionId).isNotEqualTo(123)
    assertThat(myManager.selectedSession.sessionId).isNotEqualTo(123)

    // Explicit selection of previously complete full session with non-unspecified task type.
    val session = myManager.sessionIdToSessionItems[sessionId]!!.session
    myManager.setSession(session)
    myManager.currentTaskType = ProfilerTaskType.SYSTEM_TRACE
    myManager.update()

    // Selection is successfully reflected.
    assertThat(myManager.selectedSession.sessionId).isEqualTo(123)
    // The profilingSession should have been reset as there is no ongoing session.
    assertThat(myManager.profilingSession.sessionId).isNotEqualTo(123)
  }

  private fun beginSessionHelper(device: Common.Device, process: Common.Process) {
    myManager.beginSession(1, device, process)
    myManager.update()
  }

  private fun beginSessionWithTaskHelper(device: Device, process: Common.Process, taskType: Common.ProfilerTaskType) {
    myManager.beginSession(1, device, process, taskType, false)
    myManager.update()
  }

  private fun endSessionHelper() {
    myManager.endCurrentSession()
    myManager.update()
  }


  @VisibleForTesting
  class SessionsAspectObserver : AspectObserver() {
    var selectedSessionChangedCount: Int = 0
    var profilingSessionChangedCount: Int = 0
    var sessionsChangedCount: Int = 0
    var ongoingSessionEndedCount: Int = 0

    internal fun selectedSessionChanged() {
      selectedSessionChangedCount++
    }

    internal fun profilingSessionChanged() {
      profilingSessionChangedCount++
    }

    internal fun sessionsChanged() {
      sessionsChangedCount++
    }

    internal fun ongoingSessionEnded() {
      ongoingSessionEndedCount++
    }
  }
}