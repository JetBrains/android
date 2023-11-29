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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.SessionArtifactUtils.createCpuCaptureSessionArtifact
import com.android.tools.profilers.SessionArtifactUtils.createSessionItem
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.Utils
import com.android.tools.profilers.tasks.args.singleartifact.cpu.CpuTaskArgs
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class TaskHandlerUtilsTest {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskHandlerUtilsTestChannel", myTransportService)

  private val myIdeServices = FakeIdeProfilerServices()
  private val myProfilers by lazy { StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer) }

  @Test
  fun `test executeTaskAction with non-null arg`() {
    var actionExecuted = false
    val action = {
      actionExecuted = true
    }
    var errorMessage = ""
    val errorHandler = { it: String ->
      errorMessage = it
    }
    TaskHandlerUtils.executeTaskAction(action, errorHandler)
    assertThat(actionExecuted).isTrue()
    assertThat(errorMessage).isEmpty()
  }

  @Test
  fun `test executeTaskAction with null arg`() {
    var actionExecuted = false
    val action = {
      val args: CpuTaskArgs? = null
      args!!.getCpuCaptureArtifact()
      actionExecuted = true
    }
    var errorMessage = ""
    val errorHandler = { it: String ->
      errorMessage = it
    }
    TaskHandlerUtils.executeTaskAction(action, errorHandler)
    assertThat(actionExecuted).isFalse()
    assertThat(errorMessage).isEqualTo("java.lang.NullPointerException")
  }

  @Test
  fun `test findTaskArtifact where supportsTask is false`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(createCpuCaptureSessionArtifact(myProfilers, selectedSession, 1, 3))),
    )
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionIdToSessionItems) { false }
    assertThat(artifact).isNull()
  }

  @Test
  fun `test findTaskArtifact when its Live View without artifacts`() {
    val selectedSession = Common.Session.newBuilder()
      .setSessionId(1).setEndTimestamp(100)
      .build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf() ))
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Utils.debuggableProcess { pid = 10 }
    myProfilers.sessionsManager.beginSession(device.deviceId, device, process1, Common.ProfilerTaskType.LIVE_VIEW)
    myProfilers.sessionsManager.update()
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionIdToSessionItems) { false }
    assertThat(artifact).isEqualTo(sessionIdToSessionItems[selectedSession.sessionId])
  }

  @Test
  fun `test findTaskArtifact when its not Live View without artifacts`() {
    val selectedSession = Common.Session.newBuilder()
      .setSessionId(1).setEndTimestamp(100)
      .build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf() ))
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Utils.debuggableProcess { pid = 10 }
    myProfilers.sessionsManager.beginSession(device.deviceId, device, process1, Common.ProfilerTaskType.HEAP_DUMP)
    myProfilers.sessionsManager.update()
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionIdToSessionItems) { false }
    assertThat(artifact).isNull()
  }

  @Test
  fun `test findTaskArtifact where valid artifact is present`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(createCpuCaptureSessionArtifact(myProfilers, selectedSession, 1, 3))),
    )
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionIdToSessionItems) { true }
    assertThat(artifact).isNotNull()
    assertThat(artifact!!.session.sessionId).isEqualTo(1)
  }

  @Test
  fun `test findTaskArtifact where selected session is not in session id to SessionItem mapping`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      2L to createSessionItem(myProfilers, selectedSession, 2, listOf(
        createCpuCaptureSessionArtifact(myProfilers, selectedSession, 2, 1), createCpuCaptureSessionArtifact(myProfilers, selectedSession, 2, 2)))
    )
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionIdToSessionItems) { true }
    assertThat(artifact).isNull()
  }

  @Test
  fun `test findTaskArtifact where session item has more than one child artifact`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(
        createCpuCaptureSessionArtifact(myProfilers, selectedSession, 1, 1), createCpuCaptureSessionArtifact(myProfilers, selectedSession, 1, 2)))
    )
    val artifact = TaskHandlerUtils.findTaskArtifact(selectedSession, sessionIdToSessionItems) { true }
    assertThat(artifact).isNull()
  }
}