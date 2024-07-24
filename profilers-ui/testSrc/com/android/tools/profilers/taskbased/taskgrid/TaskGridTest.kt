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
package com.android.tools.profilers.taskbased.taskgrid

import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.adtui.compose.StudioTestTheme
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
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid.TaskGrid
import com.android.tools.profilers.taskbased.task.TaskGridModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class TaskGridTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val composeTestRule = createStudioComposeTestRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var taskGridModel: TaskGridModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    taskGridModel = TaskGridModel {}
    ideProfilerServices.enableTaskBasedUx(true)
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach { myProfilers.addTaskHandler(it.key, it.value) }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme, process-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      StudioTestTheme(darkMode = false) {
        TaskGrid(taskGridModel, myProfilers.taskHandlers.keys.toList())
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme, recording-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      StudioTestTheme(darkMode = false) {
        val heapDumpArtifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers,
                                                                               Common.Session.newBuilder().setSessionId(1L).build(), 0L, 1L)
        val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, heapDumpArtifact.session, heapDumpArtifact.session.sessionId,
                                                                 listOf(heapDumpArtifact))
        TaskGrid(taskGridModel, sessionItem, myProfilers.taskHandlers)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme, process-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      StudioTestTheme(darkMode = true) {
        TaskGrid(taskGridModel, myProfilers.taskHandlers.keys.toList())
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme, recording-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      StudioTestTheme(darkMode = true) {
        val heapDumpArtifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers,
                                                                               Common.Session.newBuilder().setSessionId(1L).build(), 0L, 1L)
        val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, heapDumpArtifact.session, heapDumpArtifact.session.sessionId,
                                                                 listOf(heapDumpArtifact))
        TaskGrid(taskGridModel, sessionItem, myProfilers.taskHandlers)
      }
    }
  }

  @Test
  fun `correct number of task grid items are displayed and clickable`() {
    // There should be one task grid item for every task handler. Seven task handlers were added in the setup step of this test.
    composeTestRule.setContent {
      TaskGrid(taskGridModel, myProfilers.taskHandlers.keys.toList())
    }

    composeTestRule.onAllNodesWithTag(testTag = "TaskGridItem").assertCountEquals(7)

    composeTestRule.onNodeWithTag("System Trace", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("Callstack Sample", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("Java/Kotlin Method Recording", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("Java/Kotlin Allocations", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("Heap Dump", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("Native Allocations", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("Live View", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun `clicking task registers task type selection in model`() {
    composeTestRule.setContent {
      TaskGrid(taskGridModel, myProfilers.taskHandlers.keys.toList())
    }

    composeTestRule.onAllNodesWithTag(testTag = "TaskGridItem").assertCountEquals(7)

    composeTestRule.onNodeWithText("System Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("System Trace").performClick()

    assertThat(taskGridModel.selectedTaskType.value).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `only supported tasks show up on recording selection (single supported task)`() {
    composeTestRule.setContent {
      val heapDumpArtifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers,
                                                                             Common.Session.newBuilder().setSessionId(1L).build(), 0L, 1L)
      val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, heapDumpArtifact.session, heapDumpArtifact.session.sessionId,
                                                               listOf(heapDumpArtifact))
      TaskGrid(taskGridModel, sessionItem, myProfilers.taskHandlers)
    }

    // Heap dump recording only has one supported task (Heap Dump task).
    composeTestRule.onAllNodesWithTag("TaskGridItem").assertCountEquals(1)
    // If a task is displayed post-recording selection, then it must be enabled.
    composeTestRule.onAllNodesWithTag("TaskGridItem").assertAll(isEnabled())
  }
}