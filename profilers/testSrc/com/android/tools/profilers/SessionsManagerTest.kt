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
    myManager = SessionsManager(profilers)
    myManager.addDependency(myObserver)
      .onChange(SessionAspect.SELECTED_SESSION) { myObserver.selectedSessionChanged() }
      .onChange(SessionAspect.SESSIONS) { myObserver.sessionsChanged() }

    assertThat(myManager.sessions).isEmpty()
    assertThat(myManager.session).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testBeginSessionWithNullDeviceOrProcess() {
    myManager.beginSession(null, null)
    myManager.beginSession(null, Common.Process.getDefaultInstance())
    myManager.beginSession(Common.Device.getDefaultInstance(), null)
    assertThat(myManager.sessions).isEmpty()
    assertThat(myManager.session).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(3)
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
    assertThat(myManager.sessions).isEmpty()
    assertThat(myManager.session).isEqualTo(Common.Session.getDefaultInstance())
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

    val session = myManager.session
    assertThat(session.deviceId).isEqualTo(deviceId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.isSessionAlive).isTrue()

    val sessions = myManager.sessions.values
    assertThat(sessions).hasSize(1)
    assertThat(sessions.first()).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
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
    var session = myManager.session
    assertThat(session).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(0)

    myManager.beginSession(onlineDevice, onlineProcess)
    session = myManager.session
    assertThat(session.deviceId).isEqualTo(deviceId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.isSessionAlive).isTrue()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)

    myManager.endCurrentSession()
    session = myManager.session
    assertThat(session.deviceId).isEqualTo(deviceId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(2)
  }

  private class SessionsAspectObserver : AspectObserver() {
    var selectedSessionChangedCount: Int = 0
    var sessionsChangedCount: Int = 0

    internal fun selectedSessionChanged() {
      selectedSessionChangedCount++
    }

    internal fun sessionsChanged() {
      sessionsChangedCount++
    }
  }
}