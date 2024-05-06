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
package com.android.tools.profilers.com.android.tools.profilers.taskbased.taskgrid

import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.singleWindowApplication
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.com.android.tools.profilers.JewelThemedComposableWrapper
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid.TaskGrid
import com.android.tools.profilers.taskbased.tasks.TaskGridModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.CallstackSampleTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.JavaKotlinMethodSampleTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.JavaKotlinMethodTraceTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.cpu.SystemTraceTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.HeapDumpTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.JavaKotlinAllocationsTaskHandler
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.NativeAllocationsTaskHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class TaskGridTest {
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
    taskGridModel = TaskGridModel()
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers.addTaskHandler(ProfilerTaskType.SYSTEM_TRACE, SystemTraceTaskHandler(myManager, false))
    myProfilers.addTaskHandler(ProfilerTaskType.CALLSTACK_SAMPLE, CallstackSampleTaskHandler(myManager))
    myProfilers.addTaskHandler(ProfilerTaskType.JAVA_KOTLIN_METHOD_TRACE, JavaKotlinMethodTraceTaskHandler(myManager))
    myProfilers.addTaskHandler(ProfilerTaskType.JAVA_KOTLIN_METHOD_SAMPLE, JavaKotlinMethodSampleTaskHandler(myManager))
    myProfilers.addTaskHandler(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, JavaKotlinAllocationsTaskHandler(myManager))
    myProfilers.addTaskHandler(ProfilerTaskType.HEAP_DUMP, HeapDumpTaskHandler(myManager))
    myProfilers.addTaskHandler(ProfilerTaskType.NATIVE_ALLOCATIONS, NativeAllocationsTaskHandler(myManager))
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme, process-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      JewelThemedComposableWrapper(isDark = false) {
        TaskGrid(taskGridModel, Common.Device.newBuilder().setFeatureLevel(30).build(),
                 Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE).build(),
                 myProfilers.taskHandlers)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme, recording-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      JewelThemedComposableWrapper(isDark = false) {
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
      JewelThemedComposableWrapper(isDark = true) {
        TaskGrid(taskGridModel, Common.Device.newBuilder().setFeatureLevel(30).build(),
                 Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE).build(),
                 myProfilers.taskHandlers)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme, recording-selection based`() {
    singleWindowApplication(
      title = "Testing TaskGrid in Dark Theme",
    ) {
      JewelThemedComposableWrapper(isDark = true) {
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
      JewelThemedComposableWrapper(isDark = false) {
        TaskGrid(taskGridModel, Common.Device.newBuilder().setFeatureLevel(30).build(),
                 Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE).build(),
                 myProfilers.taskHandlers)
      }
    }

    composeTestRule.onAllNodesWithTag(testTag = "TaskGridItem").assertCountEquals(7)

    composeTestRule.onNodeWithText("System Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Callstack Sample").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Method Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Method Sample (legacy)").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Allocations").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Heap Dump").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Native Allocations").assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun `unsupported task (based off device feature level) is displayed but not clickable`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = false) {
        // Set feature level to 28, which will enable all tasks except Native Allocations.
        TaskGrid(taskGridModel, Common.Device.newBuilder().setFeatureLevel(28).build(),
                 Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE).build(),
                 myProfilers.taskHandlers)
      }
    }

    composeTestRule.onAllNodesWithTag(testTag = "TaskGridItem").assertCountEquals(7)

    composeTestRule.onNodeWithText("System Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Callstack Sample").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Method Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Method Sample (legacy)").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Allocations").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Heap Dump").assertIsDisplayed().assertIsEnabled()
    // Because the Native Allocations task requires device feature level 29, this task is not supported and this should not be clickable.
    composeTestRule.onNodeWithText("Native Allocations").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun `unsupported tasks (based off process support level being profileable) are displayed but not clickable`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = false) {
        // Set exposure level of process to profileable so only profileable-compatible tasks are enabled.
        TaskGrid(taskGridModel, Common.Device.newBuilder().setFeatureLevel(30).build(),
                 Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.PROFILEABLE).build(),
                 myProfilers.taskHandlers)
      }
    }

    composeTestRule.onAllNodesWithTag(testTag = "TaskGridItem").assertCountEquals(7)

    composeTestRule.onNodeWithText("System Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Callstack Sample").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Method Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Java/Kotlin Method Sample (legacy)").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Native Allocations").assertIsDisplayed().assertIsEnabled()
    // Java/Kotlin Allocations and Heap Dump tasks can only be done on Debuggable processes and thus should be disabled when a profileable
    // process is selected.
    composeTestRule.onNodeWithText("Java/Kotlin Allocations").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithText("Heap Dump").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun `clicking task registers task type selection in model`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = false) {
        TaskGrid(taskGridModel, Common.Device.newBuilder().setFeatureLevel(30).build(),
                 Common.Process.newBuilder().setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE).build(),
                 myProfilers.taskHandlers)
      }
    }

    composeTestRule.onAllNodesWithTag(testTag = "TaskGridItem").assertCountEquals(7)

    composeTestRule.onNodeWithText("System Trace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("System Trace").performClick()

    assertThat(taskGridModel.selectedTaskType.value).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
  }

  @Test
  fun `only supported tasks show up on recording selection (single supported task)`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = false) {
        val heapDumpArtifact = SessionArtifactUtils.createHprofSessionArtifact(myProfilers,
                                                                               Common.Session.newBuilder().setSessionId(1L).build(), 0L, 1L)
        val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, heapDumpArtifact.session, heapDumpArtifact.session.sessionId,
                                                                 listOf(heapDumpArtifact))
        TaskGrid(taskGridModel, sessionItem, myProfilers.taskHandlers)
      }
    }

    // Heap dump recording only has one supported task (Heap Dump task).
    composeTestRule.onAllNodesWithTag("TaskGridItem").assertCountEquals(1)
    // If a task is displayed post-recording selection, then it must be enabled.
    composeTestRule.onAllNodesWithTag("TaskGridItem").assertAll(isEnabled())
  }

  @Test
  fun `only supported tasks show up on recording selection (multiple supported tasks)`() {
    composeTestRule.setContent {
      JewelThemedComposableWrapper(isDark = false) {
        val javaKotlinMethodArtifact = SessionArtifactUtils.createCpuCaptureSessionArtifactWithConfig(
          myProfilers, Common.Session.newBuilder().setSessionId(1L).build(), 1L, 1L,
          Trace.TraceConfiguration.newBuilder().setArtOptions(Trace.ArtOptions.getDefaultInstance()).build())
        val sessionItem = SessionArtifactUtils.createSessionItem(myProfilers, javaKotlinMethodArtifact.session,
                                                                 javaKotlinMethodArtifact.session.sessionId,
                                                                 listOf(javaKotlinMethodArtifact))
        TaskGrid(taskGridModel, sessionItem, myProfilers.taskHandlers)
      }
    }

    // Java/Kotlin ART-based recording has two supported tasks (Java/Kotlin Method Sample and Java/Kotlin Method Trace tasks).
    composeTestRule.onAllNodesWithTag("TaskGridItem").assertCountEquals(2)
    // If a task is displayed post-recording selection, then it must be enabled.
    composeTestRule.onAllNodesWithTag("TaskGridItem").assertAll(isEnabled())
  }
}