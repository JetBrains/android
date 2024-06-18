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
package com.android.tools.profilers.taskbased.tabs.pastrecordings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.compose.StudioTestTheme
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.pastrecordings.PastRecordingsTabModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class TaskPastRecordingsTabTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)
  private val myComponents = FakeIdeProfilerComponents()

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridViewTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var pastRecordingsTabModel: PastRecordingsTabModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    pastRecordingsTabModel = PastRecordingsTabModel(myProfilers)
    ideProfilerServices.enableTaskBasedUx(true)
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach { myProfilers.addTaskHandler(it.key, it.value) }
    assertThat(pastRecordingsTabModel.recordingListModel.recordingList.value).isEmpty()
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme`() {
    singleWindowApplication(
      title = "Testing TaskPastRecordingTab",
    ) {
      StudioTestTheme(darkMode = false) {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme`() {
    singleWindowApplication(
      title = "Testing TaskPastRecordingTab",
    ) {
      StudioTestTheme(darkMode = true) {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }
  }

  @Test
  fun `selecting recording and task enable open profiler task button`() {
    composeTestRule.setContent {
      StudioTestTheme {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }

    val session = Common.Session.getDefaultInstance()
    val artConfig = Trace.TraceConfiguration.newBuilder().setArtOptions(Trace.ArtOptions.getDefaultInstance()).build()
    val artTraceArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(myProfilers, session, 1L, 1L, artConfig)
    val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, session, 1L, "Recording 1",
                                                             ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING, listOf(artTraceArtifact))
    // Populate recording list with a fake recording. The ART recording has two supported tasks.
    pastRecordingsTabModel.recordingListModel.setRecordingList(listOf(sessionItem))

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(pastRecordingsTabModel.recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)

    // Assert export button is disabled as no selection is made.
    assertThat(pastRecordingsTabModel.selectedRecording).isEqualTo(null)
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsNotEnabled()

    // Select the recording.
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].assertExists()
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].performClick()

    // Make sure process selection was registered in data model.
    assertThat(pastRecordingsTabModel.selectedRecording!!.name).isEqualTo("Recording 1")

    // Make sure a task selection was registered in the data model, as because only one task was applicable to the ART recording, it
    // was auto-selected.
    assertThat(pastRecordingsTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)

    // Make sure at this point, the open profiler task button is enabled as recording and task selection have been made,
    composeTestRule.onNodeWithTag("EnterTaskButton").assertIsEnabled()
  }

  @Test
  fun `test selection of non-exportable recording does not enable the export button click action`() {
    composeTestRule.setContent {
      StudioTestTheme (darkMode = true) {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }

    val recordingListModel = pastRecordingsTabModel.recordingListModel

    // Session with no artifacts (indicative of live task) is a non-exportable recording.
    val recording = SessionArtifactUtils.createSessionItem(myProfilers, 1L, "Recording 1", listOf())
    recordingListModel.setRecordingList(listOf(recording))

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)

    // Assert export button is disabled as no selection is made.
    assertThat(recordingListModel.selectedRecording.value).isEqualTo(null)
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsNotEnabled()

    // Select the recording.
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].assertExists()
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].performClick()

    // Assert the recording selection was registered.
    assertThat(pastRecordingsTabModel.recordingListModel.selectedRecording.value).isEqualTo(recording)

    // Assert export button is not enabled as a selection of a non-exportable artifact was made.
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsNotEnabled()
  }

  @Test
  fun `test selection of exportable recording enables export button click action`() {
    composeTestRule.setContent {
      StudioTestTheme (darkMode = true) {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }

    val recordingListModel = pastRecordingsTabModel.recordingListModel

    // System trace artifact is exportable.
    val recording = SessionArtifactUtils.createSessionItemWithSystemTraceArtifact("Recording 1", 1L, 1L, myProfilers)
    recordingListModel.setRecordingList(listOf(recording))

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)

    // Assert export button is disabled as no selection is made.
    assertThat(recordingListModel.selectedRecording.value).isEqualTo(null)
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsNotEnabled()

    // Select the recording.
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].assertExists()
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].performClick()

    // Assert the recording selection was registered.
    assertThat(pastRecordingsTabModel.recordingListModel.selectedRecording.value).isEqualTo(recording)

    // Assert export button is enabled as a selection of an exportable artifact is made.
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsEnabled()
  }

  @Test
  fun `test selection of deletable recording enables delete recording button click action`() {
    composeTestRule.setContent {
      StudioTestTheme (darkMode = true) {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }

    val recordingListModel = pastRecordingsTabModel.recordingListModel

    // Non-null session item is deletable.
    recordingListModel.setRecordingList(listOf(
      SessionArtifactUtils.createSessionItemWithSystemTraceArtifact("Recording 1", 1L, 1L, myProfilers)))

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)

    // Assert delete button is disabled as no selection is made.
    assertThat(recordingListModel.selectedRecording.value).isEqualTo(null)
    composeTestRule.onNodeWithTag("DeleteRecordingButton").assertIsNotEnabled()

    // Select the recording.
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].assertExists()
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].performClick()

    // Assert delete button is enabled as a selection of a deletable recording is made.
    composeTestRule.onNodeWithTag("DeleteRecordingButton").assertIsEnabled()
  }

  @Test
  fun `test deletion of selected recording updates rendered list`() {
    composeTestRule.setContent {
      StudioTestTheme (darkMode = true) {
        TaskPastRecordingsTab(pastRecordingsTabModel, myComponents)
      }
    }

    val recordingListModel = pastRecordingsTabModel.recordingListModel

    // Create a complete live task recording. To invoke the delete session functionality, a real session must be started and finished.
    SessionArtifactUtils.generateLiveTaskRecording(myManager)

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)

    // Select Recording 1.
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].assertExists()
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].performClick()

    // Invoke delete button is enabled as a selection of a deletable recording is made.
    composeTestRule.onNodeWithTag("DeleteRecordingButton").assertIsEnabled().assertHasClickAction()
    // Because there is a confirmation dialog when invoking the deletion button, we will simulate the user confirming the deletion
    // by performing the recording deletion explicitly.
    recordingListModel.doDeleteSelectedRecording()

    // Assert both the data model and the UI reflect the deletion of Recording 1.
    assertThat(recordingListModel.recordingList.value).hasSize(0)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(0)
    composeTestRule.onNodeWithTag("RecordingListRow").assertDoesNotExist()

    // Because the selecting recording was deleted, the selection was revoked and thus the export and delete button should be disabled.
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("DeleteRecordingButton").assertIsNotEnabled()
  }
}