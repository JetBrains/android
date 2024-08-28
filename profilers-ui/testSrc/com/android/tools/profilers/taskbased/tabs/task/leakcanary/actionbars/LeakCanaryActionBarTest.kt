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
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.ACTION_BAR_RECORDING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.ACTION_BAR_STOP_RECORDING
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
}