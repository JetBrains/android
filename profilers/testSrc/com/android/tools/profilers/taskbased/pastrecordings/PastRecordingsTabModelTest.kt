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
import com.android.tools.profiler.proto.Trace.TraceMode
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.LiveTaskHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import perfetto.protos.PerfettoConfig.TraceConfig

class PastRecordingsTabModelTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule var myGrpcChannel = FakeGrpcChannel("TaskHomeTabModelTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var pastRecordingsTabModel: PastRecordingsTabModel

  private val session = Common.Session.getDefaultInstance()

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideProfilerServices, myTimer)
    myManager = myProfilers.sessionsManager
    pastRecordingsTabModel = PastRecordingsTabModel(myProfilers)
    setupTaskHandlers(myProfilers)
    ideProfilerServices.enableTaskBasedUx(true)
  }

  private fun setupTaskHandlers(profilers: StudioProfilers) {
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(profilers.sessionsManager)
    taskHandlers.forEach { (type, handler) -> profilers.addTaskHandler(type, handler) }
  }

  private fun createGenericSessionItem(profilers: StudioProfilers): com.android.tools.profilers.sessions.SessionItem {
    val artifact = SessionArtifactUtils.createCpuCaptureSessionArtifact(profilers, session, 1L, 1L)
    return SessionArtifactUtils.createSessionItem(profilers, session, 1L, listOf(artifact))
  }

  private fun createSystemTraceSessionItem(profilers: StudioProfilers): com.android.tools.profilers.sessions.SessionItem {
    val perfettoConfig = Trace.TraceConfiguration.newBuilder().setPerfettoOptions(TraceConfig.getDefaultInstance()).build()
    val artifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(profilers, session, 1L, 1L, perfettoConfig)
    return SessionArtifactUtils.createSessionItem(profilers, session, 1L, ProfilerTaskType.SYSTEM_TRACE, listOf(artifact))
  }

  private fun createArtTraceSessionItem(profilers: StudioProfilers): com.android.tools.profilers.sessions.SessionItem {
    val artConfig =
      Trace.TraceConfiguration.newBuilder()
        .setArtOptions(Trace.ArtOptions.newBuilder().setTraceMode(TraceMode.INSTRUMENTED).build())
        .build()
    val artifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(profilers, session, 1L, 1L, artConfig)
    return SessionArtifactUtils.createSessionItem(profilers, session, 1L, ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING, listOf(artifact))
  }

  /** Creates a test profiler instance to intercept methods and allows setting a specific current task handler. */
  private fun createTestProfilers(onOpenTaskTab: () -> Unit = {}, currentTaskHandlerType: ProfilerTaskType? = null): StudioProfilers {
    val profilers =
      object : StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideProfilerServices, myTimer) {
        override fun openTaskTab() {
          onOpenTaskTab()
        }

        override fun getCurrentTaskHandler(): ProfilerTaskHandler? {
          return currentTaskHandlerType?.let { taskHandlers[it] }
        }
      }
    setupTaskHandlers(profilers)
    return profilers
  }

  @Test
  fun `test retrieval of most recent task type selection`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test retrieval of most recent recording selection`() {
    val sessionItem = createSystemTraceSessionItem(myProfilers)
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(sessionItem)
  }

  @Test
  fun `test onEnterTaskButtonClick when session has no artifacts`() {
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, listOf())
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(sessionItem)

    setCurrentTaskHandler(ProfilerTaskType.SYSTEM_TRACE)
    pastRecordingsTabModel.onEnterTaskButtonClick()
    assertThat(myManager.selectedSession).isEqualTo(session)
  }

  @Test
  fun `test onEnterTaskButtonClick when session has artifacts`() {
    val sessionItem = createSystemTraceSessionItem(myProfilers)
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(sessionItem)
    assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(sessionItem)

    setCurrentTaskHandler(ProfilerTaskType.SYSTEM_TRACE)
    pastRecordingsTabModel.onEnterTaskButtonClick()
    assertThat(myManager.selectedSession).isEqualTo(sessionItem.getChildArtifacts().first().session)
  }

  @Test
  fun `test task type selection resets after recording selection`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    pastRecordingsTabModel.recordingListModel.onRecordingSelection(createGenericSessionItem(myProfilers))
    assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }

  @Test
  fun `test task is auto-selected if it is the only supported task for a selected recording`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.UNSPECIFIED)
    pastRecordingsTabModel.recordingListModel.onRecordingSelection(createSystemTraceSessionItem(myProfilers))
    // System trace recordings have only one supported task, and thus the task gets auto-selected on recording selection.
    assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `test task type and recording selection resets after recording deletion`() {
    pastRecordingsTabModel.taskGridModel.onTaskSelection(ProfilerTaskType.LIVE_VIEW)
    myProfilers.addTaskHandler(ProfilerTaskType.LIVE_VIEW, LiveTaskHandler(myManager))
    SessionArtifactUtils.generateLiveTaskRecording(myManager, myTransportService)

    val recordingListModel = pastRecordingsTabModel.recordingListModel
    val recording = recordingListModel.recordingList.value.first()
    recordingListModel.onRecordingSelection(recording)
    assertThat(recordingListModel.selectedRecording.value).isEqualTo(recording)

    recordingListModel.doDeleteSelectedRecording()

    assertThat(recordingListModel.selectedRecording.value).isNull()
    assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
  }

  @Test
  fun `test onEnterTaskButtonClick calls openTaskTab only when unified preview is disabled`() {
    var openTaskTabCalled = false
    val testProfilers = createTestProfilers(onOpenTaskTab = { openTaskTabCalled = true })
    val testModel = PastRecordingsTabModel(testProfilers)

    testModel.recordingListModel.onRecordingSelection(createSystemTraceSessionItem(testProfilers))

    // Perform the first click to enter the task.
    testModel.onEnterTaskButtonClick()
    assertThat(testProfilers.session).isEqualTo(session)

    // Case 1: Unified Preview is DISABLED
    openTaskTabCalled = false
    ideProfilerServices.enableSystemTraceInEditor(false)
    testModel.onEnterTaskButtonClick()
    assertThat(openTaskTabCalled).isTrue()

    // Case 2: Unified Preview is ENABLED
    openTaskTabCalled = false
    ideProfilerServices.enableSystemTraceInEditor(true)
    testModel.onEnterTaskButtonClick()
    assertThat(openTaskTabCalled).isFalse()
  }

  @Test
  fun `test onEnterTaskButtonClick bypasses current task checks when system trace in editor enabled`() {
    var openTaskTabCalled = false
    // Simulate an active task tab of a different type
    val testProfilers =
      createTestProfilers(onOpenTaskTab = { openTaskTabCalled = true }, currentTaskHandlerType = ProfilerTaskType.CALLSTACK_SAMPLE)
    val testModel = PastRecordingsTabModel(testProfilers)

    testModel.recordingListModel.onRecordingSelection(createSystemTraceSessionItem(testProfilers))
    ideProfilerServices.enableSystemTraceInEditor(true)

    testModel.onEnterTaskButtonClick()

    assertThat(openTaskTabCalled).isFalse()
    assertThat(testProfilers.session).isEqualTo(session)
  }

  @Test
  fun `test onEnterTaskButtonClick calls openTaskTab only when ART trace in editor is disabled`() {
    var openTaskTabCalled = false
    val testProfilers = createTestProfilers(onOpenTaskTab = { openTaskTabCalled = true })
    val testModel = PastRecordingsTabModel(testProfilers)

    testModel.recordingListModel.onRecordingSelection(createArtTraceSessionItem(testProfilers))

    // First click to enter the task.
    testModel.onEnterTaskButtonClick()
    assertThat(testProfilers.session).isEqualTo(session)

    // Case 1: ART Trace in Editor is DISABLED
    openTaskTabCalled = false
    ideProfilerServices.enableMethodTraceInEditor(false)
    testModel.onEnterTaskButtonClick()
    assertThat(openTaskTabCalled).isTrue()

    // Case 2: ART Trace in Editor is ENABLED
    openTaskTabCalled = false
    ideProfilerServices.enableMethodTraceInEditor(true)
    testModel.onEnterTaskButtonClick()
    assertThat(openTaskTabCalled).isFalse()
  }

  @Test
  fun `test onEnterTaskButtonClick bypasses current task checks when ART trace in editor enabled`() {
    var openTaskTabCalled = false
    val testProfilers =
      createTestProfilers(onOpenTaskTab = { openTaskTabCalled = true }, currentTaskHandlerType = ProfilerTaskType.SYSTEM_TRACE)
    val testModel = PastRecordingsTabModel(testProfilers)

    testModel.recordingListModel.onRecordingSelection(createArtTraceSessionItem(testProfilers))
    ideProfilerServices.enableMethodTraceInEditor(true)

    testModel.onEnterTaskButtonClick()

    assertThat(openTaskTabCalled).isFalse()
    assertThat(testProfilers.session).isEqualTo(session)
  }

  private fun setCurrentTaskHandler(taskType: ProfilerTaskType) {
    myProfilers.setCurrentTaskHandlerFetcher { myProfilers.taskHandlers[taskType] }
  }
}
