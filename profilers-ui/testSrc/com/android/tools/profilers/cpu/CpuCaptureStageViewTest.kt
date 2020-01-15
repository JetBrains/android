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
package com.android.tools.profilers.cpu

import com.android.testutils.TestUtils
import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.analysis.CaptureNodeAnalysisModel
import com.android.tools.profilers.cpu.atrace.CpuFrameTooltip
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.JBSplitter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import javax.swing.JLabel
import javax.swing.SwingUtilities

@RunsInEdt
class CpuCaptureStageViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("FramesTest", cpuService, FakeTransportService(timer), FakeProfilerService(timer))
  @get:Rule val myEdtRule = EdtRule()

  private lateinit var stage: CpuCaptureStage
  private lateinit var profilersView: StudioProfilersView
  private val services = FakeIdeProfilerServices()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.name), services, timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                   TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.VALID_TRACE_PATH))
  }

  @Test
  fun validateCaptureStageSetsCaptureView() {
    stage.studioProfilers.stage = stage
    assertThat(profilersView.stageView).isInstanceOf(CpuCaptureStageView::class.java)
    // Streaming controls should be disabled for the capture stage.
    assertThat(profilersView.stageView.supportsStreaming()).isFalse()
    // Stage navigation should be disabled for an imported trace.
    assertThat(profilersView.stageView.supportsStageNavigation()).isFalse()
  }

  @Test
  fun statusPanelShowingWhenParsing() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    assertThat(stageView.component.getComponent(0)).isInstanceOf(StatusPanel::class.java)
  }

  @Test
  fun statusPanelIsRemovedWhenNotParsing() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    assertThat(stageView.component.getComponent(0)).isNotInstanceOf(StatusPanel::class.java)
  }

  @Test
  fun trackGroupListIsInitializedAfterParsing() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    assertThat(stageView.trackGroupList.component.componentCount).isEqualTo(2) // threads track group + tooltip component
    val treeWalker = TreeWalker(stageView.trackGroupList.component)

    val labels = treeWalker.descendants().filterIsInstance<JLabel>().toList()
    // Title label
    assertThat(labels[0].text).isEqualTo("Threads (3)")
    // Title info icon
    assertThat(labels[1].toolTipText).ignoringCase().contains("double-click on the thread name to expand/collapse it")
  }

  @Test
  fun axisComponentsAreInitialized() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    val axisComponents = TreeWalker(stageView.component)
      // Traverse depth first to make sure the order is top -> bottom
      .descendants(TreeWalker.DescendantOrder.DEPTH_FIRST)
      .filterIsInstance<AxisComponent>()

    // Minimap axis and track group axis
    assertThat(axisComponents.size).isEqualTo(2)

    // Minimap axis always uses the capture range.
    val minimapAxis = axisComponents[0]
    assertThat(minimapAxis.model.range.isSameAs(stage.capture.range)).isTrue()

    // Track group axis uses the selection range (initialized as capture range).
    val trackGroupAxis = axisComponents[1]
    assertThat(trackGroupAxis.model.range.isSameAs(stage.capture.range)).isTrue()
    val selectionRange = Range(10.0, 11.0)
    stage.minimapModel.rangeSelectionModel.selectionRange.set(selectionRange)
    assertThat(trackGroupAxis.model.range.isSameAs(selectionRange)).isTrue()
  }

  @Test
  fun analysisPanelIsInitializedAfterParsing() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    assertThat(stageView.analysisPanel.component.size).isNotSameAs(0)
  }

  @Test
  fun tooltipIsUsageWhenMouseIsOverMinimap() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)
    val splitter = TreeWalker(stageView.component).descendants().filterIsInstance<JBSplitter>().first()
    val minimap = TreeWalker(splitter.firstComponent).descendants().elementAt(2)
    // There's padding around the hover area.
    val minimapOrigin = SwingUtilities.convertPoint(minimap, Point(8, 4), stageView.component)

    assertThat(stage.tooltip).isNull()
    // Move into minimap
    ui.mouse.moveTo(minimapOrigin.x, minimapOrigin.y)
    assertThat(stage.tooltip).isInstanceOf(CpuCaptureStageCpuUsageTooltip::class.java)
  }

  @Test
  fun showTrackGroupTooltip() {
    // Load Atrace
    services.enablePerfetto(true)
    val stage = CpuCaptureStage.create(profilersView.studioProfilers, ProfilersTestData.DEFAULT_CONFIG,
                                       TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.ATRACE_PID1_PATH))
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)
    val trackGroups = stageView.trackGroupList.trackGroups

    // Initial state
    assertThat(stageView.trackGroupList.activeTooltip).isNull()

    // Frame tooltip
    val frameTracks = trackGroups[0].trackList
    val frameTracksOrigin = SwingUtilities.convertPoint(frameTracks, Point(0, 0), stageView.component)
    ui.mouse.moveTo(frameTracksOrigin.x, frameTracksOrigin.y)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(CpuFrameTooltip::class.java)

    // Thread tooltip
    // TODO: cell renderer has width=0 in this test, causing the in-cell tooltip switching logic to fail.

    // CPU core tooltip
    // TODO: use a trace with CPU cores data
  }

  @Test
  fun zoomToSelectionButton() {
    profilersView.studioProfilers.stage = stage
    val captureNode = CaptureNode(FakeCaptureNodeModel("Foo", "Bar", "123"))
    captureNode.startGlobal = 0
    captureNode.endGlobal = 10

    assertThat(profilersView.zoomToSelectionButton.isEnabled).isFalse()
    stage.multiSelectionModel.setSelection(setOf(CaptureNodeAnalysisModel(captureNode, stage.capture)))
    assertThat(profilersView.zoomToSelectionButton.isEnabled).isTrue()
    assertThat(profilersView.stageView.zoomToSelectionRange.isSameAs(Range(0.0, 10.0))).isTrue()
  }

  @Test
  fun deselectAllLabel() {
    profilersView.studioProfilers.stage = stage
    val captureNode = CaptureNode(FakeCaptureNodeModel("Foo", "Bar", "123"))
    profilersView.deselectAllLabel.setBounds(0, 0, 100, 100)
    val ui = FakeUi(profilersView.deselectAllLabel)

    assertThat(profilersView.deselectAllToolbar.isVisible).isFalse()
    stage.multiSelectionModel.setSelection(setOf(CaptureNodeAnalysisModel(captureNode, stage.capture)))
    assertThat(profilersView.deselectAllToolbar.isVisible).isTrue()
    ui.mouse.click(0, 0)
    assertThat(stage.multiSelectionModel.isEmpty).isTrue()
  }

  @Test
  fun trackGroupKeyboardShortcuts() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.trackGroupList.component)
    val selectionRange = stage.minimapModel.rangeSelectionModel.selectionRange
    var rangeLength = selectionRange.length

    // Press W to zoom in.
    ui.keyboard.setFocus(stageView.trackGroupList.component)
    ui.keyboard.press(FakeKeyboard.Key.W)
    ui.keyboard.release(FakeKeyboard.Key.W)
    assertThat(selectionRange.length).isLessThan(rangeLength)

    // Press S to zoom out.
    rangeLength = selectionRange.length
    ui.keyboard.press(FakeKeyboard.Key.S)
    ui.keyboard.release(FakeKeyboard.Key.S)
    assertThat(selectionRange.length).isGreaterThan(rangeLength)

    // Press A or left arrow to pan left.
    // First select a small range.
    selectionRange.set(selectionRange.min + 100.0, selectionRange.min + 200.0)
    var oldRange = Range(selectionRange)
    ui.keyboard.press(FakeKeyboard.Key.A)
    ui.keyboard.release(FakeKeyboard.Key.A)
    assertThat(selectionRange.min).isLessThan(oldRange.min)
    assertThat(selectionRange.max).isLessThan(oldRange.max)
    oldRange = Range(selectionRange)
    ui.keyboard.press(FakeKeyboard.Key.LEFT)
    ui.keyboard.release(FakeKeyboard.Key.LEFT)
    assertThat(selectionRange.min).isLessThan(oldRange.min)
    assertThat(selectionRange.max).isLessThan(oldRange.max)

    // Press D or right arrow to pan right.
    // First select a small range.
    selectionRange.set(selectionRange.min + 100.0, selectionRange.min + 200.0)
    oldRange = Range(selectionRange)
    ui.keyboard.press(FakeKeyboard.Key.D)
    ui.keyboard.release(FakeKeyboard.Key.D)
    assertThat(selectionRange.min).isGreaterThan(oldRange.min)
    assertThat(selectionRange.max).isGreaterThan(oldRange.max)
    oldRange = Range(selectionRange)
    ui.keyboard.press(FakeKeyboard.Key.RIGHT)
    ui.keyboard.release(FakeKeyboard.Key.RIGHT)
    assertThat(selectionRange.min).isGreaterThan(oldRange.min)
    assertThat(selectionRange.max).isGreaterThan(oldRange.max)
  }

  @Test
  fun trackGroupMouseShortcuts() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    assertThat(stageView.trackGroupList.trackGroups).isNotEmpty()
    stageView.trackGroupList.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.trackGroupList.component)
    val selectionRange = stage.minimapModel.rangeSelectionModel.selectionRange
    var rangeLength = selectionRange.length

    // Ctrl/Cmd + wheel up to zoom in.
    ui.keyboard.press(FakeKeyboard.MENU_KEY)
    ui.mouse.wheel(0, 0, -1)
    ui.keyboard.release(FakeKeyboard.MENU_KEY)
    assertThat(selectionRange.length).isLessThan(rangeLength)

    // Ctrl/Cmd + wheel down to zoom out.
    rangeLength = selectionRange.length
    ui.keyboard.press(FakeKeyboard.MENU_KEY)
    ui.mouse.wheel(0, 0, 1)
    ui.keyboard.release(FakeKeyboard.MENU_KEY)
    assertThat(selectionRange.length).isGreaterThan(rangeLength)
  }

  private class FakeCaptureNodeModel(val aName: String, val aFullName: String, val anId: String) : CaptureNodeModel {
    override fun getName(): String {
      return aName
    }

    override fun getFullName(): String {
      return aFullName
    }

    override fun getId(): String {
      return anId
    }
  }
}