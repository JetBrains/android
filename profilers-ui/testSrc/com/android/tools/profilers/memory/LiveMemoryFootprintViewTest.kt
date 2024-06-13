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
package com.android.tools.profilers.memory

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.TooltipModel
import com.android.tools.adtui.model.ViewBinder
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerTooltipMouseAdapter
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StageWithToolbarView
import com.android.tools.profilers.StreamingStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.LiveMemoryFootprintModel
import com.android.tools.profilers.memory.LiveMemoryFootprintView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.intellij.ui.components.JBPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

open class LiveMemoryFootprintViewTest {
  protected var myTimer = FakeTimer()
  private val myComponents = FakeIdeProfilerComponents()
  private val myIdeServices = FakeIdeProfilerServices()
  private val myTransportService = FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S,
                                                        Common.Process.ExposureLevel.PROFILEABLE)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("MainMemoryProfilerLiveViewTest", myTransportService, FakeEventService())

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var myProfilersView: StudioProfilersView
  private lateinit var myModel: LiveMemoryFootprintModel
  private lateinit var profilers: StudioProfilers

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer)
    // Advance the clock to make sure StudioProfilers has a chance to select device + process.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myModel = LiveMemoryFootprintModel(profilers)
    myModel.enter()
    myProfilersView = SessionProfilersView(profilers, myComponents, disposableRule.disposable)
  }

  @Test
  fun testTooltipComponentAsFirstElement() {
    val memoryFootprintView = LiveMemoryFootprintView(myProfilersView, myModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponent = RangeTooltipComponent(myTimeline, myContent)
    memoryFootprintView.populateUi(rangeTooltipComponent);
    val treeWalker = TreeWalker(memoryFootprintView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)
    // Check for tooltip presence in live view component
    assertThat(tooltipComponent.size).isEqualTo(1)
    val usageView = getMainComponent(memoryFootprintView)
    // Check for tooltip to be first element of usage view
    assertThat(usageView.getComponent(0)).isInstanceOf(RangeTooltipComponent::class.java)
  }

  @Test
  fun testJPanelComponentAsSecondElementOfMainComponent() {
    val memoryFootprintView = LiveMemoryFootprintView(myProfilersView, myModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponent = RangeTooltipComponent(myTimeline, myContent)
    memoryFootprintView.populateUi(rangeTooltipComponent);
    val treeWalker = TreeWalker(memoryFootprintView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)
    // Check for tooltip presence in live view component
    assertThat(tooltipComponent.size).isEqualTo(1)
    val usageView = getMainComponent(memoryFootprintView)
    // Check for tooltip to be first element of usage view
    assertThat(usageView.getComponent(1)).isInstanceOf(JBPanel::class.java)
  }

  @Test
  fun testWithoutPopulateUiCalledComponentIsEmpty() {
    val memoryFootprintView = LiveMemoryFootprintView(myProfilersView, myModel)
    val treeWalker = TreeWalker(memoryFootprintView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)
    // Check for tooltip presence in live view component
    assertThat(tooltipComponent.size).isEqualTo(0)
    val usageView = getMainComponent(memoryFootprintView)
    assertThat(usageView.size.height).isEqualTo(0)
    assertThat(usageView.size.width).isEqualTo(0)
  }

  @Test
  fun testHasContextMenuItems() {
    myComponents.clearContextMenuItems()
    val memoryFootprintView = LiveMemoryFootprintView(myProfilersView, myModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val tooltipComponent = RangeTooltipComponent(myTimeline, myContent)
    memoryFootprintView.populateUi(tooltipComponent);
    val items = myComponents.allContextMenuItems
    // 4 items and 1 separator, garbage collection and seperator
    // Attach, Detach, Separator, Zoom in, Zoom out
    assertThat(items.size).isEqualTo(8)
    assertThat(items[0].text).isEqualTo(ContextMenuItem.SEPARATOR.text)
    assertThat(items[1].text).isEqualTo("Force garbage collection")
    assertThat(items[2].text).isEqualTo(ContextMenuItem.SEPARATOR.text)
    assertThat(items[3].text).isEqualTo(StageWithToolbarView.ATTACH_LIVE)
    assertThat(items[4].text).isEqualTo(StageWithToolbarView.DETACH_LIVE)
    assertThat(items[5].text).isEqualTo(ContextMenuItem.SEPARATOR.text)
    assertThat(items[6].text).isEqualTo(StageWithToolbarView.ZOOM_IN)
    assertThat(items[7].text).isEqualTo(StageWithToolbarView.ZOOM_OUT)
  }

  @Test
  fun testToolbarHasGcButton() {
    val memoryFootprintView = LiveMemoryFootprintView(myProfilersView, myModel)
    val toolbar = memoryFootprintView.toolbar.getComponent(0) as JPanel
    assertThat(toolbar.components).asList().containsExactly(
      memoryFootprintView.garbageCollectionButton,
    )
  }

  @Test
  fun testShowTooltipComponentAfterRegisterToolTip() {
    val memoryFootprintView = spy(
      LiveMemoryFootprintView(myProfilersView, myModel))
    val tooltipComponent = mock<JComponent>()
    Mockito.doReturn(tooltipComponent).whenever(memoryFootprintView).tooltipComponent
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponent = RangeTooltipComponent(myTimeline, myContent)
    memoryFootprintView.populateUi(rangeTooltipComponent);
    val binder = ViewBinder<StageView<*>, TooltipModel, TooltipView>()
    val stage = mock<StreamingStage>()
    memoryFootprintView.registerTooltip(binder, rangeTooltipComponent, stage)
    verify(stage, times(1)).tooltip = MockitoKt.any(TooltipModel::class.java)
    verify(tooltipComponent, times(1)).addMouseListener(MockitoKt.any(ProfilerTooltipMouseAdapter::class.java))
  }

  private fun getMainComponent(stageView: LiveMemoryFootprintView) = TreeWalker(stageView.component)
    .descendants()
    .filterIsInstance<JPanel>()
    .first()
}