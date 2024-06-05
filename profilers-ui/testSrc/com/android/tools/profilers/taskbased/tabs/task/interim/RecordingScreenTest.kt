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
package com.android.tools.profilers.taskbased.tabs.task.interim

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.compose.JewelTestTheme
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModelTest.Companion.startFakeRecording
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.SystemTraceTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.HeapDumpTaskHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class RecordingScreenTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, SystemTraceTaskHandler(myManager, false))
    myProfilers.addTaskHandler(ProfilerTaskType.HEAP_DUMP, HeapDumpTaskHandler(myManager))
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme, stoppable (CPU) recording screen`() {
    singleWindowApplication(
      title = "Testing Recording Screen in Light Theme",
    ) {
      JewelTestTheme(darkMode = false) {
        setupRecording(isStoppable = true)
        val stage = CpuProfilerStage(myProfilers)
        val recordingScreenModel = stage.recordingScreenModel!!
        // Enable the stop button by starting a fake recording to simulate an ongoing recording.
        startFakeRecording(stage.recordingModel)
        RecordingScreen(recordingScreenModel)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme, stoppable (CPU) recording screen`() {
    singleWindowApplication(
      title = "Testing Recording Screen in Dark Theme",
    ) {
      JewelTestTheme(darkMode = true) {
        setupRecording(isStoppable = true)
        val stage = CpuProfilerStage(myProfilers)
        val recordingScreenModel = stage.recordingScreenModel!!
        // Enable the stop button by starting a fake recording to simulate an ongoing recording.
        startFakeRecording(stage.recordingModel)
        RecordingScreen(recordingScreenModel)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme, non stoppable (memory) recording screen`() {
    singleWindowApplication(
      title = "Testing Recording Screen in Light Theme",
    ) {
      JewelTestTheme(darkMode = false) {
        setupRecording(isStoppable = false)
        val recordingScreenModel = MainMemoryProfilerStage(myProfilers).recordingScreenModel!!
        RecordingScreen(recordingScreenModel)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme, non stoppable (memory) recording screen`() {
    singleWindowApplication(
      title = "Testing Recording Screen in Dark Theme",
    ) {
      JewelTestTheme(darkMode = true) {
        setupRecording(isStoppable = false)
        val recordingScreenModel = MainMemoryProfilerStage(myProfilers).recordingScreenModel!!
        RecordingScreen(recordingScreenModel)
      }
    }
  }

  @Test
  fun `test stoppable (CPU) recording screen`() {
    val stage = CpuProfilerStage(myProfilers)
    composeTestRule.setContent {
      JewelTestTheme {
        setupRecording(isStoppable = true)
        val recordingScreenModel = stage.recordingScreenModel!!
        RecordingScreen(recordingScreenModel)
      }
    }

    composeTestRule.onNodeWithTag("RecordingScreenMessage").assertTextEquals(TaskBasedUxStrings.RECORDING_IN_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithTag("StopRecordingButton").assertIsDisplayed().assertExists().assertIsNotEnabled().assertHasClickAction()

    // Enable the stop button by starting a fake recording to simulate an ongoing recording.
    startFakeRecording(stage.recordingModel)
    composeTestRule.onNodeWithTag("StopRecordingButton").assertIsDisplayed().assertExists().assertIsEnabled().assertHasClickAction()
  }

  @Test
  fun `test non stoppable (memory) recording screen`() {
    composeTestRule.setContent {
      JewelTestTheme {
        setupRecording(isStoppable = false)
        val recordingScreenModel = MainMemoryProfilerStage(myProfilers).recordingScreenModel!!
        RecordingScreen(recordingScreenModel)
      }
    }

    composeTestRule.onNodeWithTag("RecordingScreenMessage").assertTextEquals("Saving a heap dump...").assertIsDisplayed()
    composeTestRule.onNodeWithTag("StopRecordingButton").assertDoesNotExist()
  }

  @Test
  fun `test clicking stop button disables button and enters stopping state`() {
    val stage = CpuProfilerStage(myProfilers)
    val recordingScreenModel = stage.recordingScreenModel!!
    composeTestRule.setContent {
      JewelTestTheme (darkMode = true) {
        setupRecording(isStoppable = false)
        RecordingScreen(recordingScreenModel)
      }
    }

    composeTestRule.onNodeWithText(TaskBasedUxStrings.RECORDING_IN_PROGRESS).assertIsDisplayed().assertExists()
    composeTestRule.onNodeWithTag("StopRecordingButton").assertIsDisplayed().assertExists().assertIsNotEnabled().assertHasClickAction()

    // Enable the stop button by starting a fake recording to simulate an ongoing recording.
    startFakeRecording(stage.recordingModel)
    composeTestRule.onNodeWithTag("StopRecordingButton").assertIsDisplayed().assertExists().assertIsEnabled().assertHasClickAction()

    // Make sure that the model shows the stop button has not been clicked yet.
    assertThat(recordingScreenModel.isStopButtonClicked.value).isFalse()
    composeTestRule.onNodeWithTag("StopRecordingButton").performClick()
    // After clicking the stop button, the model state should reflect such click.
    assertThat(recordingScreenModel.isStopButtonClicked.value).isTrue()

    // Because the stop button has been clicked, the stop button should have been disabled to prevent further stop attempts.
    composeTestRule.onNodeWithTag("StopRecordingButton").assertIsNotEnabled()

    // Make sure the in progress recording text has been replaced with the stopping text, as the UI has entered the stopping state.
    composeTestRule.onNodeWithText(TaskBasedUxStrings.RECORDING_IN_PROGRESS).assertDoesNotExist()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.STOPPING_IN_PROGRESS).assertIsDisplayed()
    // Make sure the timer has been replaced with the stopping time warning, as the UI has entered the stopping state.
    composeTestRule.onNodeWithText("min").assertDoesNotExist()
    composeTestRule.onNodeWithText(TaskBasedUxStrings.STOPPING_TIME_WARNING).assertIsDisplayed()
  }

  private fun setupRecording(isStoppable: Boolean) {
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer,
                                      if (isStoppable) Common.ProfilerTaskType.SYSTEM_TRACE else Common.ProfilerTaskType.HEAP_DUMP)
  }
}