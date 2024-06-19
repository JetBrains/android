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
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.Utils
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.selections.recordings.RecordingListModelTest
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.LiveTaskHandler
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import perfetto.protos.PerfettoConfig.TraceConfig

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
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach{ (type, handler)  -> myProfilers.addTaskHandler(type, handler) }
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

  @Test
  fun `test task is auto-selected if it is the only supported task for a selected recording`() {
    // Reset task selection for testing purposes.
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.UNSPECIFIED)
    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
    val session = Common.Session.getDefaultInstance()
    val perfettoConfig = Trace.TraceConfiguration.newBuilder().setPerfettoOptions(TraceConfig.getDefaultInstance()).build()
    val systemTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers, session, 1L, 1L, perfettoConfig)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, listOf(systemTraceArtifact))
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    // System trace recordings have only one supported task, and thus the task gets auto-selected on recording selection.
    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test task type and recording selection resets after recording deletion`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.LIVE_VIEW)
    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.LIVE_VIEW)

    myProfilers.addTaskHandler(ProfilerTaskType.LIVE_VIEW, LiveTaskHandler(myManager))
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Utils.debuggableProcess { pid = 10; deviceId = 1 }
    RecordingListModelTest.startAndStopSession(device, process, Common.ProfilerTaskType.LIVE_VIEW, myManager)
    val recordingListModel = pastRecordingsTabModel.recordingListModel
    // Select the recording.
    val recording = recordingListModel.recordingList.value.first()
    recordingListModel.onRecordingSelection(recording)
    Truth.assertThat(recordingListModel.selectedRecording.value).isEqualTo(recording)

    // Perform deletion of selected recording.
    recordingListModel.doDeleteSelectedRecording()

    // Make sure task type and recording selection have been reset.
    Truth.assertThat(recordingListModel.selectedRecording.value).isEqualTo(null)
    Truth.assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }
}