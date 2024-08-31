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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.tools.adtui.compose.utils.StudioComposeTestRule
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.leakcanarylib.data.LeakTraceNodeType
import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.leakcanarylib.data.Node
import com.android.tools.leakcanarylib.data.ReferencingField
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ExpandedLeakDetailsTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("ExpandedLeakDetailsTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var mockLeakCanaryModel: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @get:Rule
  val composeTestRule = StudioComposeTestRule.createStudioComposeTestRule()

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    mockLeakCanaryModel = LeakCanaryModel(profilers)
  }

  @Test
  fun `text expand node details displays correctly`() {
    val referencingField = ReferencingField("random.classname", ReferencingField.ReferencingFieldType.INSTANCE_FIELD,
                                            false, "referenceName")
    val node = Node(LeakTraceNodeType.INSTANCE, "random.classname", LeakingStatus.YES,
                    "A Strong garbage collection.", 1024,
                    10, listOf("Note 1", "Note 2"), referencingField)

    composeTestRule.setContent {
      LeakNodeDetails(node = node)
    }

    composeTestRule.onNodeWithText("Leaking").assertIsDisplayed()
    composeTestRule.onNodeWithText("Yes").assertIsDisplayed()

    composeTestRule.onNodeWithText("Why").assertIsDisplayed()
    composeTestRule.onNodeWithText("A Strong garbage collection.").assertIsDisplayed()

    composeTestRule.onNodeWithText("Retained Bytes: 1024 bytes").assertIsDisplayed()
    composeTestRule.onNodeWithText("Referencing Objects: 10").assertIsDisplayed()

    composeTestRule.onNodeWithText("More info").assertExists()
    composeTestRule.onNodeWithText("Note 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Note 2").assertIsDisplayed()

    composeTestRule.onNodeWithText("More info").performClick() // hide section
    composeTestRule.onNodeWithText("Note 1").assertDoesNotExist()
    composeTestRule.onNodeWithText("Note 2").assertDoesNotExist()
  }

  @Test
  fun `test expand node details display correctly when node is not leaking`() {
    val node = Node(LeakTraceNodeType.INSTANCE, "random.classname", LeakingStatus.NO,
                        "Not Leaking", null,
                        null, listOf(), null)

    composeTestRule.setContent {
      LeakNodeDetails(node = node)
    }

    composeTestRule.onNodeWithText("Leaking").assertIsDisplayed()
    composeTestRule.onNodeWithText("No").assertIsDisplayed()

    composeTestRule.onNodeWithText("Why").assertIsDisplayed()
    composeTestRule.onNodeWithText("Not Leaking").assertIsDisplayed()

    composeTestRule.onNodeWithText("Referencing Field:").assertDoesNotExist()
    composeTestRule.onNodeWithText("Retained Bytes:").assertDoesNotExist()
    composeTestRule.onNodeWithText("Referencing Objects:").assertDoesNotExist()

    composeTestRule.onNodeWithText("More info").assertDoesNotExist()
  }
}