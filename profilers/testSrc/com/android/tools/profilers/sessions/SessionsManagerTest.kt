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
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class SessionsManagerTest {

  private val myProfilerService = FakeProfilerService(false)
  private val myMemoryService = FakeMemoryService()
  private val myCpuService = FakeCpuService()

  @get:Rule
  val myThrown = ExpectedException.none()
  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
      "SessionsManagerTestChannel",
      myProfilerService,
      myMemoryService,
      myCpuService,
      FakeEventService(),
      FakeNetworkService.newBuilder().build()
  )

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myObserver: SessionsAspectObserver

  @Before
  fun setup() {
    myObserver = SessionsAspectObserver()
    myProfilers = StudioProfilers(
        myGrpcChannel.client,
        FakeIdeProfilerServices(),
        FakeTimer()
    )
    myManager = myProfilers.sessionsManager
    myManager.addDependency(myObserver)
      .onChange(SessionAspect.SELECTED_SESSION) { myObserver.selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { myObserver.profilingSessionChanged() }
      .onChange(SessionAspect.SESSIONS) { myObserver.sessionsChanged() }

    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testBeginSessionWithNullDeviceOrProcess() {
    myManager.beginSession(null, null)
    myManager.beginSession(null, Common.Process.getDefaultInstance())
    myManager.beginSession(Common.Device.getDefaultInstance(), null)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(0)
  }

  @Test
  fun testBeginSessionWithOfflineDeviceOrProcess() {
    val offlineDevice = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.DISCONNECTED).build()
    val onlineDevice = Common.Device.newBuilder().setDeviceId(2).setState(Common.Device.State.ONLINE).build()
    val offlineProcess = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.DEAD).build()
    val onlineProcess = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.DEAD).build()

    myManager.beginSession(offlineDevice, offlineProcess)
    myManager.beginSession(offlineDevice, onlineProcess)
    myManager.beginSession(onlineDevice, offlineProcess)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(0)
  }

  @Test
  fun testBeginSession() {
    val deviceId = 1
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(deviceId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()
    myManager.beginSession(onlineDevice, onlineProcess)

    val session = myManager.selectedSession
    assertThat(session.deviceId).isEqualTo(deviceId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    val sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)
  }

  @Test
  fun testBeginSessionCannotRunTwice() {
    val deviceId = 1
    val pid1 = 10
    val pid2 = 20
    val onlineDevice = Common.Device.newBuilder().setDeviceId(deviceId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess1 = Common.Process.newBuilder().setPid(pid1).setState(Common.Process.State.ALIVE).build()
    val onlineProcess2 = Common.Process.newBuilder().setPid(pid2).setState(Common.Process.State.ALIVE).build()
    myManager.beginSession(onlineDevice, onlineProcess1)

    myThrown.expect(AssertionError::class.java)
    myManager.beginSession(onlineDevice, onlineProcess2)
  }

  @Test
  fun testEndSession() {
    val deviceId = 1
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(deviceId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()

    // endSession calls on no active session is a no-op
    myManager.endCurrentSession()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(0)

    myManager.beginSession(onlineDevice, onlineProcess)
    var session = myManager.selectedSession
    assertThat(session.deviceId).isEqualTo(deviceId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    var sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)

    myManager.endCurrentSession()
    session = myManager.selectedSession
    assertThat(session.deviceId).isEqualTo(deviceId)
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
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    myManager.beginSession(device, process1)
    myManager.endCurrentSession()
    val session1 = myManager.selectedSession
    myManager.beginSession(device, process2)
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
  fun testSwitchingNonProfilingSession() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    myManager.beginSession(device, process1)
    myManager.endCurrentSession()
    val session1 = myManager.selectedSession
    myManager.beginSession(device, process2)
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
  fun testSessionArtifactsUpToDate() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    val session1Timestamp = 1L
    val session2Timestamp = 2L
    myProfilerService.setTimestampNs(session1Timestamp)
    myManager.beginSession(device, process1)
    myManager.endCurrentSession()
    val session1 = myManager.selectedSession
    myProfilerService.setTimestampNs(session2Timestamp)
    myManager.beginSession(device, process2)
    myManager.endCurrentSession()
    val session2 = myManager.selectedSession

    var sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionItems[0] as SessionItem
    var sessionItem1 = sessionItems[1] as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.canExpand()).isFalse()
    assertThat(sessionItem0.isExpanded).isFalse()
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.canExpand()).isFalse()
    assertThat(sessionItem1.isExpanded).isFalse()

    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myCpuService.addTraceInfo(cpuTraceInfo)
    myManager.update()

    // Sessions should now be expandable
    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    sessionItem0 = sessionItems[0] as SessionItem
    sessionItem1 = sessionItems[1] as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.canExpand()).isTrue()
    assertThat(sessionItem0.isExpanded).isFalse()
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.canExpand()).isTrue()
    assertThat(sessionItem1.isExpanded).isFalse()

    // Expand the first session, the hprof and cpu capture artifacts should now be included and sorted in ascending order.
    sessionItem0.isExpanded = true
    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(4)
    sessionItem0 = sessionItems[0] as SessionItem
    val hprofItem = sessionItems[1] as HprofSessionArtifact
    val cpuCaptureItem = sessionItems[2] as CpuCaptureSessionArtifact
    sessionItem1 = sessionItems[3] as SessionItem

    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.canExpand()).isTrue()
    assertThat(sessionItem0.isExpanded).isTrue()
    assertThat(hprofItem.session).isEqualTo(session2)
    assertThat(hprofItem.timestampNs).isEqualTo(heapDumpTimestamp - session2Timestamp)
    assertThat(cpuCaptureItem.session).isEqualTo(session2)
    assertThat(cpuCaptureItem.timestampNs).isEqualTo(cpuTraceTimestamp - session2Timestamp)
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.canExpand()).isTrue()
    assertThat(sessionItem1.isExpanded).isFalse()

    // Collpase the session again
    sessionItem0.isExpanded = false
    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    sessionItem0 = sessionItems[0] as SessionItem
    sessionItem1 = sessionItems[1] as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.canExpand()).isTrue()
    assertThat(sessionItem0.isExpanded).isFalse()
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.canExpand()).isTrue()
    assertThat(sessionItem1.isExpanded).isFalse()
  }

  private class SessionsAspectObserver : AspectObserver() {
    var selectedSessionChangedCount: Int = 0
    var profilingSessionChangedCount: Int = 0
    var sessionsChangedCount: Int = 0

    internal fun selectedSessionChanged() {
      selectedSessionChangedCount++
    }

    internal fun profilingSessionChanged() {
      profilingSessionChangedCount++
    }

    internal fun sessionsChanged() {
      sessionsChangedCount++
    }
  }
}