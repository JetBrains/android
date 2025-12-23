/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.actionbars

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.leakcanary.FakeLeakCanaryCommandHandler
import com.android.tools.profilers.leakcanary.LeakCanaryHeapDumper
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.ACTION_BAR_RECORDING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.ACTION_BAR_STOP_RECORDING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_ANALYSIS
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_FORCE_DUMP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_RETAINED_OBJECT
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_WAITING_HEAP_DUMP
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class LeakCanaryActionBarTest: WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakCanaryModelTestChannel", transportService)

  @get:Rule
  val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  private lateinit var profilers: StudioProfilers
  private lateinit var leakCanaryModel: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    leakCanaryModel = LeakCanaryModel(profilers)
  }

  @Test
  fun `test action bar display when recording`() {
    leakCanaryModel.setIsRecording(true)
    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(ACTION_BAR_RECORDING).assertIsDisplayed()
    composeTestRule.onNodeWithText(ACTION_BAR_STOP_RECORDING).assertIsDisplayed()
  }

  @Test
  fun `test action bar display when not recording`() {
    leakCanaryModel.setIsRecording(false)
    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(ACTION_BAR_RECORDING).assertDoesNotExist()
    composeTestRule.onNodeWithText(ACTION_BAR_STOP_RECORDING).assertDoesNotExist()
  }

  @Test
  fun `test stop recording button action`() {
    val startTimestamp = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTimestamp))
    transportService.setCommandHandler(Commands.Command.CommandType.CHECK_LEAKCANARY_PRESENT,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTimestamp))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTimestamp))
    leakCanaryModel.startListening()
    // Recording is in progress
    assertTrue(leakCanaryModel.isRecording.value)
    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(ACTION_BAR_STOP_RECORDING).performClick()
    // Click on 'Stop Recording' has stopped the recording
    assertFalse(leakCanaryModel.isRecording.value)
  }

  @Test
  fun `retained object count from logcat is displayed`() {
    leakCanaryModel.setIsRecording(true)
    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }

    // Test for singular case
    leakCanaryModel.setObjectRetainedCount(1)
    val singularMessage = "1 $LEAKCANARY_RETAINED_OBJECT. $LEAKCANARY_WAITING_HEAP_DUMP 5 ${LEAKCANARY_RETAINED_OBJECT}s."
    composeTestRule.onNodeWithText(singularMessage).assertIsDisplayed()

    // Test for plural case
    leakCanaryModel.setObjectRetainedCount(2)
    val pluralMessage = "2 ${LEAKCANARY_RETAINED_OBJECT}s. $LEAKCANARY_WAITING_HEAP_DUMP 5 ${LEAKCANARY_RETAINED_OBJECT}s."
    composeTestRule.onNodeWithText(pluralMessage).assertIsDisplayed()

    // Ensure analysis progress bar is not shown
    composeTestRule.onNodeWithTag("AnalysisProgressBar").assertDoesNotExist()
  }

  @Test
  fun `analysis progress from logcat is displayed`() {
    leakCanaryModel.setIsRecording(true)
    // Set retained object count to meet the requirement to trigger analysis view
    leakCanaryModel.setObjectRetainedCount(leakCanaryModel.requiredRetainedObjectCount)
    leakCanaryModel.setAnalysisProgress(50)

    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }

    // Verify that the analysis progress UI is displayed
    composeTestRule.onNodeWithText(LEAKCANARY_ANALYSIS).assertIsDisplayed()
    composeTestRule.onNodeWithTag("AnalysisProgressBar").assertIsDisplayed()
  }

  @Test
  fun `status is cleared when analysis is complete`() {
    leakCanaryModel.setIsRecording(true)
    leakCanaryModel.setObjectRetainedCount(leakCanaryModel.requiredRetainedObjectCount)
    leakCanaryModel.setAnalysisProgress(100)

    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }

    composeTestRule.onNodeWithText(LEAKCANARY_ANALYSIS).assertIsDisplayed()

    leakCanaryModel.setObjectRetainedCount(0)
    leakCanaryModel.setAnalysisProgress(0)


    composeTestRule.onNodeWithText(LEAKCANARY_ANALYSIS).assertDoesNotExist()
    composeTestRule.onNodeWithTag("AnalysisProgressBar").assertDoesNotExist()
  }

  @Test
  fun `test force heap dump button not visible when flag is disabled`() {
    ideProfilerServices.enableLeakCanaryMilestone2(false)
    leakCanaryModel.setIsRecording(true)
    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertDoesNotExist()
  }

  @Test
  fun `test force heap dump button visible and clickable when flag is enabled`() {
    ideProfilerServices.enableLeakCanaryMilestone2(true)
    val mockHeapDumper: LeakCanaryHeapDumper = mock()

    leakCanaryModel = LeakCanaryModel(profilers, mockHeapDumper)
    leakCanaryModel.setIsRecording(true)
    // Button is enabled when at least one object is retained.
    leakCanaryModel.setObjectRetainedCount(1)

    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertIsDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertIsEnabled()
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).performClick()

    // Verify that the triggerAndAnalyze() method was called exactly once
    verify(mockHeapDumper, times(1)).triggerAndAnalyze()
  }

  @Test
  fun `test force heap dump button visible but disabled when flag is enabled and count is 0`() {
    ideProfilerServices.enableLeakCanaryMilestone2(true)

    val mockHeapDumper: LeakCanaryHeapDumper = mock()

    leakCanaryModel = LeakCanaryModel(profilers, mockHeapDumper)
    leakCanaryModel.setIsRecording(true)
    // Button is disabled when 0 objects are retained.
    leakCanaryModel.setObjectRetainedCount(0)

    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertIsDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertIsNotEnabled()
  }

  @Test
  fun `test force heap dump button disabled when analysis is in progress`() {
    ideProfilerServices.enableLeakCanaryMilestone2(true)
    val mockHeapDumper: LeakCanaryHeapDumper = mock()

    leakCanaryModel = LeakCanaryModel(profilers, mockHeapDumper)
    leakCanaryModel.setIsRecording(true)
    // Button is disabled when analysis is in progress, even if objects are retained.
    leakCanaryModel.setObjectRetainedCount(1)
    leakCanaryModel.setAnalysisProgress(50)

    composeTestRule.setContent {
      LeakCanaryActionBar(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertIsDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_FORCE_DUMP).assertIsNotEnabled()
  }
}
