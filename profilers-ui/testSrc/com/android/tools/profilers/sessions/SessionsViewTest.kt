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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeGrpcServer
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionsViewTest {

  @get:Rule
  var myGrpcServer = FakeGrpcServer(
      "StudioProfilerTestChannel",
      FakeProfilerService(false)
  )

  private lateinit var myProfilers: StudioProfilers
  private lateinit var mySessionsManager: SessionsManager
  private lateinit var mySessionsView: SessionsView

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
        myGrpcServer.client,
        FakeIdeProfilerServices(),
        FakeTimer()
    )
    mySessionsManager = myProfilers.sessionsManager
    mySessionsView = SessionsView(mySessionsManager)
  }

  @Test
  fun testSessionsListUpToDate() {
    val sessionArtifacts = mySessionsView.sessionsList.model
    assertThat(sessionArtifacts.size).isEqualTo(0)

    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    mySessionsManager.beginSession(device, process1)
    var session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session1)
    assertThat(mySessionsView.sessionsList.selectedIndex).isEqualTo(0)

    mySessionsManager.endCurrentSession()
    session1 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(1)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session1)
    assertThat(mySessionsView.sessionsList.selectedIndex).isEqualTo(0)

    mySessionsManager.beginSession(device, process2)
    val session2 = mySessionsManager.selectedSession
    assertThat(sessionArtifacts.size).isEqualTo(2)
    assertThat(sessionArtifacts.getElementAt(0).session).isEqualTo(session2)
    assertThat(sessionArtifacts.getElementAt(1).session).isEqualTo(session1)
    assertThat(mySessionsView.sessionsList.selectedIndex).isEqualTo(0)
  }
}