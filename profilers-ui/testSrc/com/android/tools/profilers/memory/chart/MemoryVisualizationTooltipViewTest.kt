/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryCaptureObjectTestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JLayeredPane

class MemoryVisualizationTooltipViewTest {
  private val timer = FakeTimer()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("MEMORY_TEST_CHANNEL", FakeTransportService(timer))

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var fakeIdeProfilerComponents: FakeIdeProfilerComponents
  private lateinit var stage: MainMemoryProfilerStage
  private lateinit var visualizationView: MemoryVisualizationView
  private lateinit var tooltip: MemoryVisualizationTooltipView
  private lateinit var simpleNode: ClassifierSetHNode
  private val visualizationModel = MemoryVisualizationModel()

  @Before
  fun before() {
    val loader = FakeCaptureObjectLoader()
    loader.setReturnImmediateFuture(true)
    val fakeIdeProfilerServices = FakeIdeProfilerServices()
    fakeIdeProfilerComponents = FakeIdeProfilerComponents()
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), fakeIdeProfilerServices, FakeTimer())
    stage = MainMemoryProfilerStage(profilers, loader)
    val profilersView = SessionProfilersView(profilers, fakeIdeProfilerComponents, disposableRule.disposable)
    visualizationView = MemoryVisualizationView(stage.captureSelection, profilersView)

    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    visualizationModel.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    simpleNode = ClassifierSetHNode(visualizationModel, heapSet, 0)
    val range = Range(0.0, simpleNode.end.toDouble())
    val chart = HTreeChart.Builder<ClassifierSetHNode>(simpleNode, range, HeapSetNodeHRenderer())
      .setGlobalXRange(range)
      .setRootVisible(false)
      .build()
    tooltip = MemoryVisualizationTooltipView(chart, JLayeredPane(), VisualizationTooltipModel(range, visualizationModel))
  }

  @Test
  fun labelsForNameCount() {
    tooltip.showTooltip(simpleNode)
    val labels = TreeWalker(tooltip.tooltipComponent).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(2)
    assertThat(labels[0].text).contains(simpleNode.name)
    assertThat(labels[1].text).contains("Count: " + simpleNode.duration)
  }

  @Test
  fun labelsForNameSize() {
    visualizationModel.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_SIZE
    tooltip.showTooltip(simpleNode)
    val labels = TreeWalker(tooltip.tooltipComponent).descendants().filterIsInstance<JLabel>()
    assertThat(labels).hasSize(2)
    assertThat(labels[0].text).contains(simpleNode.name)
    assertThat(labels[1].text).contains("Size: " + (simpleNode.duration / 1000.0))
  }
}