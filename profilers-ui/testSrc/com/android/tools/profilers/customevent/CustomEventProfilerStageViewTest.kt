/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.stdui.StreamingScrollbar
import com.android.tools.adtui.trackgroup.TrackGroupListPanel
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JList

class CustomEventProfilerStageViewTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  private lateinit var stage: CustomEventProfilerStage
  private lateinit var view: StudioProfilersView

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CustomEventProfilerStageViewTest", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply { enableCustomEventVisualization(true) }
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    transportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE)
    stage = CustomEventProfilerStage(profilers)
    profilers.stage = stage

    //Initialize the view after the stage, otherwise it will create views for monitoring stage
    view = StudioProfilersView(profilers, FakeIdeProfilerComponents())

  }

  @Test
  fun trackGroupListIsCreated() {
    val stageView = CustomEventProfilerStageView(view, stage)
    stage.enter()
    assertThat(stageView.trackGroupList).isInstanceOf(TrackGroupListPanel::class.java)
  }

  @Test
  fun expectedStageViewIsCreated() {
    assertThat(view.stageView).isInstanceOf(CustomEventProfilerStageView::class.java)
  }

  @Test
  fun testExpectedUIComponents() {
    //Test that the stage view has the following expected components: JList of all the tracks, the timeline, and the scrollbar
    val customEventProfilerStageView = view.stageView as CustomEventProfilerStageView
    val treeWalker = TreeWalker(customEventProfilerStageView.component)

    //track lists
    val trackList = treeWalker.descendants().filterIsInstance(JList::class.java)
    assertThat(trackList.size).isEqualTo(2)

    //timeline
    val timeline = treeWalker.descendants().filterIsInstance(AxisComponent::class.java)
    assertThat(timeline.size).isEqualTo(1)

    //scrollbar
    val scrollbar = treeWalker.descendants().filterIsInstance(StreamingScrollbar::class.java)
    assertThat(scrollbar.size).isEqualTo(1)
  }

}


