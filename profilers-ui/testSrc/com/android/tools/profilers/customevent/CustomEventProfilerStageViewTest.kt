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
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerScrollbar
import com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import javax.swing.JList

class CustomEventProfilerStageViewTest {

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, true)

  private lateinit var stage: CustomEventProfilerStage
  private lateinit var view: StudioProfilersView

  @Before
  fun setUp() {
    val services = FakeIdeProfilerServices().apply { enableCustomEventVisualization(true) }
    val profilers = StudioProfilers(ProfilerClient(CustomEventProfilerStageViewTest::class.java.simpleName), services, timer)
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
    assertThat(stageView.trackGroupList.model.size).isEqualTo(1)
  }

  @Test
  fun expectedStageViewIsCreated() {
    assertThat(view.stageView).isInstanceOf(CustomEventProfilerStageView::class.java)
  }

  @Test
  fun testTooltipComponentIsFirstChild() {
    val customEventProfilerStageView = view.stageView as CustomEventProfilerStageView
    val treeWalker = TreeWalker(customEventProfilerStageView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)[0]
    assertThat(tooltipComponent.parent.components[0]).isEqualTo(tooltipComponent)
  }

  @Test
  fun testExpectedUIComponents() {
    //Test that the stage view has the following expected components: JList of all the tracks, the timeline, and the scrollbar
    val customEventProfilerStageView = view.stageView as CustomEventProfilerStageView
    val treeWalker = TreeWalker(customEventProfilerStageView.component)

    //track list
    val tracklist = treeWalker.descendants().filterIsInstance(JList::class.java)
    assertThat(tracklist.size).isEqualTo(1)

    //timeline
    val timeline = treeWalker.descendants().filterIsInstance(AxisComponent::class.java)
    assertThat(timeline.size).isEqualTo(1)

    //scrollbar
    val scrollbar = treeWalker.descendants().filterIsInstance(ProfilerScrollbar::class.java)
    assertThat(scrollbar.size).isEqualTo(1)
  }

}


