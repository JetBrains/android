/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.leakcanary.LeakFilterScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LeakCanaryFilterBarTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @Rule @JvmField val grpcChannel = FakeGrpcChannel("LeakCanaryFilterBarTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var leakCanaryModel: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @get:Rule val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    leakCanaryModel = LeakCanaryModel(profilers, null, Dispatchers.Unconfined)
  }

  @Test
  fun `test filter bar interactions update model`() {
    composeTestRule.setContent { LeakCanaryFilterBar(leakCanaryModel = leakCanaryModel) }

    composeTestRule.onNodeWithTag("LeakCanaryRegexCheckbox").performClick()
    assertEquals(true, leakCanaryModel.useRegex.value)

    composeTestRule.onNodeWithTag("LeakCanaryMatchCaseCheckbox").performClick()
    assertEquals(true, leakCanaryModel.matchCase.value)
  }

  @Test
  fun `test dropdown interaction updates scope model`() {
    composeTestRule.setContent { LeakCanaryFilterBar(leakCanaryModel = leakCanaryModel) }

    composeTestRule.onNodeWithTag("LeakFilterScopeDropdown").performClick()
    composeTestRule.onNodeWithText("App").performClick()

    assertEquals(LeakFilterScope.APP, leakCanaryModel.filterScope.value)
  }

  @Test
  fun `test search text field clears when close icon is clicked`() {
    leakCanaryModel.setSearchQuery("test query")
    composeTestRule.setContent { LeakCanaryFilterBar(leakCanaryModel = leakCanaryModel) }
    composeTestRule.onNodeWithContentDescription("Clear search").performClick()

    assertEquals("", leakCanaryModel.searchQuery.value)
  }

  @Test
  fun `test UI updates from model changes`() {
    composeTestRule.setContent { LeakCanaryFilterBar(leakCanaryModel = leakCanaryModel) }
    composeTestRule.onNodeWithTag("LeakCanarySearchTextField").assertTextContains("")
    composeTestRule.onNodeWithTag("LeakCanaryRegexCheckbox").assertIsOff()
    leakCanaryModel.setSearchQuery("model driven query")
    leakCanaryModel.setUseRegex(true)
    composeTestRule.onNodeWithTag("LeakCanarySearchTextField").assertTextContains("model driven query")
    composeTestRule.onNodeWithTag("LeakCanaryRegexCheckbox").assertIsOn()
  }
}
