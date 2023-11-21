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
package com.android.tools.profilers.com.android.tools.profilers.taskbased.tabs.pastrecordings.recordinglist

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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils.createSessionItemWithSystemTraceArtifact
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.com.android.tools.profilers.JewelThemedComposableWrapper
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.selections.recordings.RecordingListModel
import com.android.tools.profilers.taskbased.tabs.pastrecordings.recordinglist.RecordingList
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class RecordingListTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridViewTestChannel", myTransportService, FakeEventService())

  private val myComponents = FakeIdeProfilerComponents()

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var recordingListModel: RecordingListModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach {  myProfilers.addTaskHandler(it.key, it.value)  }
    recordingListModel = RecordingListModel(myProfilers, taskHandlers) {}
    ideProfilerServices.enableTaskBasedUx(true)
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme`() {
    recordingListModel.setRecordingList(listOf(createSessionItemWithSystemTraceArtifact("Recording 1", 1L, 1L, myProfilers)))
    singleWindowApplication(
      title = "Testing TaskGridView",
    ) {
      JewelThemedComposableWrapper(isDark = true) {
        RecordingList(recordingListModel = recordingListModel, myComponents)
      }
    }
  }

  @Test
  fun `test import file renders in UI and is reflected in data model`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = true) {
        RecordingList(recordingListModel, myComponents)
      }
    }

    recordingListModel.setRecordingList(listOf(createSessionItemWithSystemTraceArtifact("Recording 1", 1L, 1L, myProfilers)))

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)
  }

  @Test
  fun `test selection of exportable recording enables export button click action`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = true) {
        RecordingList(recordingListModel, myComponents)
      }
    }

    // System trace artifact is exportable.
    recordingListModel.setRecordingList(listOf(createSessionItemWithSystemTraceArtifact("Recording 1", 1L, 1L, myProfilers)))

    // Assert both the data model and the UI reflect the past recording entry.
    assertThat(recordingListModel.recordingList.value).hasSize(1)
    composeTestRule.onAllNodesWithTag("RecordingListRow").assertCountEquals(1)

    // Assert export button is disabled as no selection is made.
    assertThat(recordingListModel.selectedRecording.value).isEqualTo(null)
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsNotEnabled()

    // Select the recording.
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].assertHasClickAction()
    composeTestRule.onAllNodesWithTag("RecordingListRow")[0].performClick()

    // Assert export button is enabled as a selection of an exportable artifact is made.
    composeTestRule.onNodeWithTag("ExportRecordingButton").assertIsEnabled()
  }
}
