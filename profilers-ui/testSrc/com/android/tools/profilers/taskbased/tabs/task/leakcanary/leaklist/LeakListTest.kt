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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.leakcanarylib.data.AnalysisSuccess
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakType
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_INSTALLATION_REQUIRED_MESSAGE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_LEAK_HEADER_TEXT
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_OCCURRENCES_HEADER_TEXT
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_NO_LEAK_FOUND_MESSAGE
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class LeakListTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakListTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var leakCanaryModel: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @get:Rule
  val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    leakCanaryModel = LeakCanaryModel(profilers)
  }

  @Test
  fun `test leak list view when recoding and no leak is available`() {
    leakCanaryModel.setIsRecording(true)
    composeTestRule.setContent {
      LeakListView(leakCanaryModel = leakCanaryModel)
    }

    // Headers are always displayed
    composeTestRule.onNodeWithText(LEAKCANARY_LEAK_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_OCCURRENCES_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_INSTALLATION_REQUIRED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_NO_LEAK_FOUND_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun `test leak list view when not recoding and no leak is available`() {
    leakCanaryModel.setIsRecording(false)
    composeTestRule.setContent {
      LeakListView(leakCanaryModel = leakCanaryModel)
    }

    // Headers are always displayed
    composeTestRule.onNodeWithText(LEAKCANARY_LEAK_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_OCCURRENCES_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(LEAKCANARY_INSTALLATION_REQUIRED_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithText(LEAKCANARY_NO_LEAK_FOUND_MESSAGE).isDisplayed()
  }

  @Test
  fun `test leak list view when leak is available, text is not displayed`() {
    val mockLeak1 = Leak(LeakType.APPLICATION_LEAKS, 100, "Signature1", 5, listOf())
    val mockLeak2 = Leak(LeakType.LIBRARY_LEAKS, 203, "Signature2", 9, listOf())
    val mockLeak3 = Leak(LeakType.APPLICATION_LEAKS, 405, "Signature3", 11, listOf())
    leakCanaryModel.addLeaks(listOf(mockLeak1, mockLeak2, mockLeak3))
    composeTestRule.setContent {
      LeakListView(leakCanaryModel = leakCanaryModel)
    }
    composeTestRule.onNodeWithText(LEAKCANARY_LEAK_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_OCCURRENCES_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT).isDisplayed()
    composeTestRule.onNodeWithText(LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun `test leak list view when leak is available, list leaks`() {
    val mockLeak1 = Leak(LeakType.APPLICATION_LEAKS, 2048, "Signature1", 501, listOf())
    val mockLeak2 = Leak(LeakType.LIBRARY_LEAKS, 2048*2, "Signature2", 504, listOf())
    val mockLeak3 = Leak(LeakType.APPLICATION_LEAKS, 2048*4, "Signature3", 506, listOf())
    leakCanaryModel.addLeaks(listOf(mockLeak1, mockLeak2, mockLeak3))
    composeTestRule.setContent {
      LeakListView(leakCanaryModel = leakCanaryModel)
    }

    composeTestRule.onNodeWithText("501").isDisplayed()
    composeTestRule.onNodeWithText("504").isDisplayed()
    composeTestRule.onNodeWithText("506").isDisplayed()
    composeTestRule.onNodeWithText("2 kb").isDisplayed()
    composeTestRule.onNodeWithText("4 kb").isDisplayed()
    composeTestRule.onNodeWithText("8 kb").isDisplayed()
  }

  @Test
  fun `test leak selection updates the selected leak`() {
    val mockLeak1 = Leak(LeakType.APPLICATION_LEAKS, 2048, "Signature1", 501, listOf())
    val mockLeak2 = Leak(LeakType.LIBRARY_LEAKS, 2048*2, "Signature2", 504, listOf())
    val mockLeak3 = Leak(LeakType.APPLICATION_LEAKS, 2048*4, "Signature3", 506, listOf())
    leakCanaryModel.addLeaks(listOf(mockLeak1, mockLeak2, mockLeak3))
    composeTestRule.setContent {
      LeakListView(leakCanaryModel = leakCanaryModel)
    }

    composeTestRule.onNodeWithText("501").performClick()
    assertEquals(mockLeak1, leakCanaryModel.selectedLeak.value)

    composeTestRule.onNodeWithText("504").performClick()
    assertEquals(mockLeak2, leakCanaryModel.selectedLeak.value)
  }

  @Test
  fun `test leak list view updated when new leaks added`() {
    val mockLeak1 = Leak(LeakType.APPLICATION_LEAKS, 2048, "Signature1", 501, listOf())
    val mockLeak2 = Leak(LeakType.LIBRARY_LEAKS, 2048*2, "Signature2", 504, listOf())
    val mockLeak3 = Leak(LeakType.APPLICATION_LEAKS, 2048*4, "Signature3", 506, listOf())
    val analysisSuccess = AnalysisSuccess(mock(File::class.java), 232323L,
                                           232323L, 342323L, mapOf(),
                                          listOf(mockLeak1, mockLeak2, mockLeak3))
    leakCanaryModel.addLeaks(analysisSuccess.leaks)
    composeTestRule.setContent {
      LeakListView(leakCanaryModel = leakCanaryModel)
    }

    composeTestRule.onNodeWithText("501").isDisplayed()
    composeTestRule.onNodeWithText("504").isDisplayed()
    composeTestRule.onNodeWithText("506").isDisplayed()
    composeTestRule.onNodeWithText("2 kb").isDisplayed()
    composeTestRule.onNodeWithText("4 kb").isDisplayed()
    composeTestRule.onNodeWithText("8 kb").isDisplayed()

    composeTestRule.onNodeWithText("504").performClick()
    assertEquals(mockLeak2, leakCanaryModel.selectedLeak.value)

    val mockLeak4 = Leak(LeakType.APPLICATION_LEAKS, 2048*8, "Signature4", 509, listOf())
    val analysisSuccess2 = AnalysisSuccess(mock(File::class.java), 232323L,
                                          232323L, 342323L, mapOf(), listOf(mockLeak4))
    leakCanaryModel.addLeaks(analysisSuccess2.leaks)
    composeTestRule.onNodeWithText("509").isDisplayed()
    composeTestRule.onNodeWithText("16 kb").isDisplayed()
  }
}