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
package com.android.tools.profilers.com.android.tools.profilers.cpu

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.TooltipModel
import com.android.tools.adtui.model.ViewBinder
import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StageWithToolbarView
import com.android.tools.profilers.StreamingStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuUsageView
import com.android.tools.profilers.cpu.LiveCpuUsageModel
import com.android.tools.profilers.cpu.LiveCpuUsageView
import com.android.tools.profilers.event.FakeEventService
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JPanel

class LiveCpuUsageViewTest {

  private val myTimer = FakeTimer()
  private val myComponents = FakeIdeProfilerComponents()
  private val myIdeServices = FakeIdeProfilerServices()
  private val myTransportService = FakeTransportService(myTimer, true, AndroidVersion.VersionCodes.S,
                                                        Common.Process.ExposureLevel.PROFILEABLE)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel("CpuProfilerLiveViewTestChannel", myTransportService, FakeEventService())

  @get:Rule
  val myEdtRule = EdtRule()

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var myProfilersView: StudioProfilersView
  private lateinit var myLiveAllocationModel: LiveCpuUsageModel
  private lateinit var myStage: StreamingStage

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myStage = CpuProfilerStage(profilers)
    myLiveAllocationModel = LiveCpuUsageModel(profilers, myStage)
    myLiveAllocationModel.enter()
    myProfilersView = SessionProfilersView(profilers, myComponents, disposableRule.disposable)
  }

  @Test
  fun testTooltipIsPresentUnderDetailsPanel() {
    val cpuProfilerLiveView = LiveCpuUsageView(myProfilersView, myLiveAllocationModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponentFirst = RangeTooltipComponent(myTimeline, myContent)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst)
    val treeWalker = TreeWalker(cpuProfilerLiveView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)
    // Check for tooltip presence in live view component
    assertThat(tooltipComponent.size).isEqualTo(1)

    val topLevelPanel = TreeWalker(cpuProfilerLiveView.component)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    // Top level component, has the main panel
    assertThat(topLevelPanel.getComponent(0)).isInstanceOf(JPanel::class.java)

    val detailsPanel = TreeWalker(topLevelPanel)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    assertThat(detailsPanel.getComponent(0)).isInstanceOf(JPanel::class.java)

    val rangeTooltipComponent = TreeWalker(detailsPanel)
      .descendants()
      .filterIsInstance<RangeTooltipComponent>()
      .first()
    // Range tooltip is present under DetailsPanel
    assertThat(rangeTooltipComponent).isInstanceOf(RangeTooltipComponent::class.java)
  }

  @Test
  fun testCpuUsageIsPresentUnderMainPanel() {
    val cpuProfilerLiveView = LiveCpuUsageView(myProfilersView, myLiveAllocationModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponentFirst = RangeTooltipComponent(myTimeline, myContent)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst)
    val treeWalker = TreeWalker(cpuProfilerLiveView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)
    // Check for tooltip presence in live view component
    assertThat(tooltipComponent.size).isEqualTo(1)

    val topLevelPanel = TreeWalker(cpuProfilerLiveView.component)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    // Top level component, has the main panel
    assertThat(topLevelPanel.getComponent(0)).isInstanceOf(JPanel::class.java)

    val detailsPanel = TreeWalker(topLevelPanel)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    assertThat(detailsPanel.getComponent(0)).isInstanceOf(JPanel::class.java)

    val mainPanel = TreeWalker(detailsPanel)
      .descendants()
      .filterIsInstance<JPanel>()
      .last()
    assertThat(mainPanel).isInstanceOf(JPanel::class.java)

    val usagePanel = TreeWalker(detailsPanel)
      .descendants()
      .filterIsInstance<CpuUsageView>()
      .first()
    assertThat(usagePanel).isInstanceOf(CpuUsageView::class.java)
  }

  @Test
  fun testCpuThreadsIsPresentUnderMainPanel() {
    val cpuProfilerLiveView = LiveCpuUsageView(myProfilersView, myLiveAllocationModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponentFirst = RangeTooltipComponent(myTimeline, myContent)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst)
    val treeWalker = TreeWalker(cpuProfilerLiveView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)
    // Check for tooltip presence in live view component
    assertThat(tooltipComponent.size).isEqualTo(1)

    val topLevelPanel = TreeWalker(cpuProfilerLiveView.component)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    // Top level component, has the main panel
    assertThat(topLevelPanel.getComponent(0)).isInstanceOf(JPanel::class.java)

    val detailsPanel = TreeWalker(topLevelPanel)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    // Details panel has 2 component
    assertThat(detailsPanel.getComponent(0)).isInstanceOf(JPanel::class.java)

    val mainPanel = TreeWalker(detailsPanel)
      .descendants()
      .filterIsInstance<JPanel>()
      .last()
    assertThat(mainPanel).isInstanceOf(JPanel::class.java)

    val cpuState = TreeWalker(mainPanel)
      .descendants()
      .filterIsInstance<JPanel>()
      .last()
    assertThat(cpuState).isInstanceOf(JPanel::class.java)

    val cpuThread = TreeWalker(cpuState)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()

    assertThat(cpuThread).isInstanceOf(JPanel::class.java)

    // Since cpuThread is also type of JPanel, we make sure cpuThread is at the last level
    val childOfCpuThread = TreeWalker(cpuThread).descendants().last()
    // since no child, it wil be same as parent
    assertThat(childOfCpuThread).isEqualTo(cpuThread)
  }

  @Test
  fun testHasNoContextMenuItemsWithoutRegisterTooltip() {
    myComponents.clearContextMenuItems()
    var cpuProfilerLiveView = LiveCpuUsageView(myProfilersView, myLiveAllocationModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponentFirst = RangeTooltipComponent(myTimeline, myContent)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst)
    val items = myComponents.allContextMenuItems

    // Context menu will not be present here since its present in main liveView component
    assertThat(items.size).isEqualTo(0)
  }

  @Test
  fun testHasContextMenuItems() {
    myComponents.clearContextMenuItems()
    var cpuProfilerLiveView = LiveCpuUsageView(myProfilersView, myLiveAllocationModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponentFirst = RangeTooltipComponent(myTimeline, myContent)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst);
    val binder = ViewBinder<StageView<*>, TooltipModel, TooltipView>()
    val stage = MockitoKt.mock<StreamingStage>()
    cpuProfilerLiveView.registerTooltip(binder, rangeTooltipComponentFirst, stage)
    val items = myComponents.allContextMenuItems

    // 2 context menu. One for CPU Usage and CPU Threads
    // Each having 5 items
    assertThat(items.size).isEqualTo(10)
    assertThat(items[0].text).isEqualTo(StageWithToolbarView.ATTACH_LIVE)
    assertThat(items[1].text).isEqualTo(StageWithToolbarView.DETACH_LIVE)
    assertThat(items[2]).isEqualTo(ContextMenuItem.SEPARATOR)
    assertThat(items[3].text).isEqualTo(StageWithToolbarView.ZOOM_IN)
    assertThat(items[4].text).isEqualTo(StageWithToolbarView.ZOOM_OUT)

    assertThat(items[5].text).isEqualTo(StageWithToolbarView.ATTACH_LIVE)
    assertThat(items[6].text).isEqualTo(StageWithToolbarView.DETACH_LIVE)
    assertThat(items[7]).isEqualTo(ContextMenuItem.SEPARATOR)
    assertThat(items[8].text).isEqualTo(StageWithToolbarView.ZOOM_IN)
    assertThat(items[9].text).isEqualTo(StageWithToolbarView.ZOOM_OUT)
  }

  @Test
  fun testShowTooltipSeekComponentHoverUsageView() {
    val cpuProfilerLiveView = LiveCpuUsageView(myProfilersView, myLiveAllocationModel)
    val myTimeline: Timeline = DefaultTimeline()
    val myContent = JPanel(BorderLayout())
    val rangeTooltipComponentFirst = RangeTooltipComponent(myTimeline, myContent)
    cpuProfilerLiveView.populateUi(rangeTooltipComponentFirst)
    val instructions = TreeWalker(cpuProfilerLiveView.component).descendants().filterIsInstance<InstructionsPanel>()
    assertThat(instructions.size).isEqualTo(0)

    val ui = FakeUi(cpuProfilerLiveView.component)
    val topLevelPanel = TreeWalker(cpuProfilerLiveView.component)
      .descendants()
      .filterIsInstance<JPanel>()
      .first()
    val usageViewPosition = ui.getPosition(topLevelPanel)

    assertThat(usageViewPosition.y).isEqualTo(0)
    assertThat(ui.isShowing(topLevelPanel)).isTrue()
    // Tooltip seek component is visible
    assertThat(ui.isShowing(topLevelPanel.getComponent(0))).isTrue()

    val rangeTooltipComponent = TreeWalker(cpuProfilerLiveView.component)
      .descendants()
      .filterIsInstance<RangeTooltipComponent>()
      .first()

    ui.targetMouseEvent(100, 100)
    // Graph is visible
    assertThat(ui.isShowing(rangeTooltipComponent)).isTrue()
  }
}