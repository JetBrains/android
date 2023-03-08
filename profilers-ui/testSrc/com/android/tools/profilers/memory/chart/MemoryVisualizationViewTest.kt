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

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.RangeTimeScrollBar
import com.android.tools.adtui.TooltipComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.hchart.HTreeChart
import com.android.tools.adtui.chart.hchart.HTreeChartVerticalScrollBar
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.ClassGrouping
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryCaptureObjectTestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JComboBox
import javax.swing.JComponent

class MemoryVisualizationViewTest {
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
  }

  @Test
  fun toolbarComponentContainsComboboxOfFilter() {
    assertThat(visualizationView.toolbarComponents).hasSize(1)
    assertThat(visualizationView.toolbarComponents[0]).isInstanceOf(JComboBox::class.java)
    val comboBox = visualizationView.toolbarComponents[0] as JComboBox<*>
    assertThat(comboBox.model.size).isEqualTo(MemoryVisualizationModel.XAxisFilter.values().size)
  }

  @Test
  fun contextMenuInstalledOnVisualization() {
    val component = visualizationView.component
    MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    visualizationView.onSelectionChanged(true)
    val walker = TreeWalker(component)
    val chart = walker.descendants().filterIsInstance<HTreeChart<ClassifierSetHNode>>().first() as JComponent
    assertThat(fakeIdeProfilerComponents.getCodeLocationSupplier(chart)).isNotNull()
  }

  @Test
  fun filterPersistBeforeAndAfterSelection() {
    val component = visualizationView.component
    MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    var hitCount = 0
    stage.captureSelection.filterHandler.addMatchCountResultListener {  hitCount++}
    visualizationView.onSelectionChanged(true)
    assertThat(hitCount).isEqualTo(1) // Validate we refreshed the filter
    visualizationView.onSelectionChanged(false)
    assertThat(hitCount).isEqualTo(2) // Validate we refreshed the filter
  }

  @Test
  fun classGroupingResetOnDeselected() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    heapSet.classGrouping = ClassGrouping.ARRANGE_BY_CLASS
    assertThat(heapSet.classGrouping).isEqualTo(ClassGrouping.ARRANGE_BY_CLASS)
    visualizationView.onSelectionChanged(true)
    assertThat(heapSet.classGrouping).isEqualTo(ClassGrouping.ARRANGE_BY_CALLSTACK)
    visualizationView.onSelectionChanged(false)
    assertThat(heapSet.classGrouping).isEqualTo(ClassGrouping.ARRANGE_BY_CLASS)
  }

  @Test
  fun exposedComponentRemainsSameInstance() {
    val component = visualizationView.component
    MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    // All updates to the capture shouldn't change the exposed component
    assertThat(visualizationView.component).isEqualTo(component)
    visualizationView.onSelectionChanged(true)
    assertThat(visualizationView.component).isEqualTo(component)
    visualizationView.onSelectionChanged(false)
    assertThat(visualizationView.component).isEqualTo(component)
  }

  @Test
  fun expectedComponents() {
    val component = visualizationView.component
    MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    visualizationView.onSelectionChanged(true)
    val walker = TreeWalker(component)
    assertThat(walker.descendants().filterIsInstance<AxisComponent>()).hasSize(1)
    assertThat(walker.descendants().filterIsInstance<RangeTimeScrollBar>()).hasSize(1)
    assertThat(walker.descendants().filterIsInstance<HTreeChart<ClassifierSetHNode>>()).hasSize(1)
    assertThat(walker.descendants().filterIsInstance<HTreeChartVerticalScrollBar<ClassifierSetHNode>>()).hasSize(1)
  }

  @Test
  fun axisComponentSizeMatchsFilterSize() {
    val component = visualizationView.component
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    visualizationView.onSelectionChanged(true)
    var axis = TreeWalker(component).descendants().filterIsInstance<AxisComponent>().first()
    assertThat(axis.model.dataRange).isWithin(.002).of((heapSet.allocationSize).toDouble())
    (visualizationView.toolbarComponents[0] as JComboBox<MemoryVisualizationModel.XAxisFilter>).selectedItem =
      MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    axis = TreeWalker(component).descendants().filterIsInstance<AxisComponent>().first()
    assertThat(axis.model.dataRange).isWithin(.002).of(heapSet.totalObjectCount.toDouble())
  }

  @Test
  fun chartHasTooltip() {
    val component = visualizationView.component
    MemoryCaptureObjectTestUtils.createAndSelectHeapSet(stage)
    visualizationView.onSelectionChanged(true)
    val walker = TreeWalker(component)
    val chart = walker.descendants().filterIsInstance<HTreeChart<ClassifierSetHNode>>().first() as JComponent
    assertThat(chart.mouseMotionListeners).isNotEmpty()
    assertThat(chart.mouseMotionListeners[0].javaClass.name).contains(HTreeChart::class.java.name)
    assertThat(chart.mouseMotionListeners[1].javaClass.name).contains(TooltipComponent::class.java.name)
    assertThat(chart.mouseMotionListeners[2]).isInstanceOf(MemoryVisualizationTooltipView::class.java)
  }
}