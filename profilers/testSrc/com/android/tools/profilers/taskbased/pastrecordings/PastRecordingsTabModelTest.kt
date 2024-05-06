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
package com.android.tools.profilers.taskbased.pastrecordings

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PastRecordingsTabModelTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskHomeTabModelTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var pastRecordingsTabModel: PastRecordingsTabModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideProfilerServices, myTimer)
    myManager = myProfilers.sessionsManager
    pastRecordingsTabModel = PastRecordingsTabModel(myProfilers)
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Test
  fun `test retrieval of most recent task type selection`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test retrieval of most recent recording selection`() {
    val session = Common.Session.getDefaultInstance()
    val systemTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifact(myProfilers, session, 1L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, listOf(systemTraceArtifact))
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    Truth.assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(sessionItem)
  }

  @Test
  fun `test onEnterTaskButtonClick when session has no artifacts`() {
    val session = Common.Session.getDefaultInstance()
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, listOf())
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    Truth.assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(sessionItem)

    pastRecordingsTabModel.onEnterTaskButtonClick()
    Truth.assertThat(myProfilers.sessionsManager.selectedSession).isEqualTo(session)
  }

  @Test
  fun `test onEnterTaskButtonClick when session has artifacts`() {
    val session = Common.Session.getDefaultInstance()
    val systemTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifact(myProfilers, session, 1L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, listOf(systemTraceArtifact))
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    Truth.assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(sessionItem)

    pastRecordingsTabModel.onEnterTaskButtonClick()
    Truth.assertThat(myProfilers.sessionsManager.selectedSession).isEqualTo(systemTraceArtifact.session)
  }

  @Test
  fun `test task type selection resets after recording selection`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    val session = Common.Session.getDefaultInstance()
    val systemTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifact(myProfilers, session, 1L, 1L)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, listOf(systemTraceArtifact))
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)

    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }
}