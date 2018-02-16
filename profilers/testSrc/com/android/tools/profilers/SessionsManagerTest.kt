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
package com.android.tools.profilers

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class SessionsManagerTest {

  @get:Rule
  val myThrown = ExpectedException.none()
  @get:Rule
  var myGrpcServer = FakeGrpcServer("StudioProfilerTestChannel", FakeProfilerService(false))

  private lateinit var myManager: SessionsManager
  private lateinit var myObserver: SessionsAspectObserver

  @Before
  fun setup() {
    myObserver = SessionsAspectObserver()
    val profilers = StudioProfilers(myGrpcServer.client, FakeIdeProfilerServices(), FakeTimer())
    myManager = profilers.sessionsManager
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

    myManager.beginSession(device, process1)
    myManager.endCurrentSession()
    val session1 = myManager.selectedSession
    myManager.beginSession(device, process2)
    myManager.endCurrentSession()
    val session2 = myManager.selectedSession

    val sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    assertThat(sessionItems[0].session).isEqualTo(session2)
    assertThat(sessionItems[1].session).isEqualTo(session1)
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