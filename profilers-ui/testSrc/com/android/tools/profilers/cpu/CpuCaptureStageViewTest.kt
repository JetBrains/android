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

import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.TestAdtUiCursorsProvider
import com.android.tools.adtui.common.replaceAdtUiCursorWithPredefinedCursor
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.analysis.CaptureNodeAnalysisModel
import com.android.tools.profilers.cpu.analysis.JankAnalysisModel
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameTimelineEvent
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTooltip
import com.android.tools.profilers.cpu.systemtrace.CpuFrameTooltip
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip
import com.android.tools.profilers.cpu.systemtrace.RenderSequence
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTooltip
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture
import com.android.tools.profilers.cpu.systemtrace.VsyncTooltip
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerServiceInstance
import com.intellij.ui.JBSplitter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import perfetto.protos.PerfettoTrace
import java.awt.Cursor
import java.awt.HeadlessException
import java.awt.Point
import javax.swing.JLabel
import javax.swing.SwingUtilities

@RunsInEdt
class CpuCaptureStageViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("FramesTest", cpuService, FakeTransportService(timer), FakeProfilerService(timer))

  @get:Rule
  val edtRule = EdtRule()

  /**
   * For initializing [com.intellij.ide.HelpTooltip].
   */
  @get:Rule
  val appRule = ApplicationRule()

  private lateinit var stage: CpuCaptureStage
  private lateinit var profilersView: StudioProfilersView
  private val services = FakeIdeProfilerServices()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                   resolveWorkspacePath(CpuProfilerUITestUtils.VALID_TRACE_PATH).toFile(), 123L)

    ApplicationManager.getApplication().registerServiceInstance(AdtUiCursorsProvider::class.java, TestAdtUiCursorsProvider())
    replaceAdtUiCursorWithPredefinedCursor(AdtUiCursorType.GRAB, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR))
    replaceAdtUiCursorWithPredefinedCursor(AdtUiCursorType.GRABBING, Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
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
  }

  @Test
  fun emptyTraceShowsWarningMessage() {
    val stage = CpuCaptureStage.create(profilersView.studioProfilers, ProfilersTestData.DEFAULT_CONFIG,
                                       resolveWorkspacePath(CpuProfilerUITestUtils.EMPTY_SIMPLEPERF_PATH).toFile(), 123L)
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()

    // Verify the normal splitter UI is gone.
    // Instead there should be a warning message.
    assertThat(stageView.component.getComponent(0)).isNotInstanceOf(JBSplitter::class.java)
    val treeWalker = TreeWalker(stageView.component)
    val warningLabel = treeWalker.descendants().filterIsInstance<JLabel>().first()
    assertThat(warningLabel.text).contains("This trace doesn't contain any data.")
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
    val stage = CpuCaptureStage.create(profilersView.studioProfilers, ProfilersTestData.DEFAULT_CONFIG,
                                       resolveWorkspacePath(CpuProfilerUITestUtils.ATRACE_TRACE_PATH).toFile(), 123L)
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    val trackGroups = stageView.trackGroupList.trackGroups
    trackGroups.forEach { it.overlay.setBounds(0, 0, 500, 100) }

    // Initial state
    assertThat(stageView.trackGroupList.activeTooltip).isNull()

    // Frame tooltip
    val displayTrackUi = FakeUi(trackGroups[0].overlay)
    displayTrackUi.mouse.moveTo(0, 0)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(CpuFrameTooltip::class.java)
    val surfaceflingerTrackPos = trackGroups[0].trackList.indexToLocation(1)
    displayTrackUi.mouse.moveTo(surfaceflingerTrackPos.x, surfaceflingerTrackPos.y)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(SurfaceflingerTooltip::class.java)
    val vsyncTrackPos = trackGroups[0].trackList.indexToLocation(2)
    displayTrackUi.mouse.moveTo(vsyncTrackPos.x, vsyncTrackPos.y)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(VsyncTooltip::class.java)
    val bufferQueuePos = trackGroups[0].trackList.indexToLocation(3)
    displayTrackUi.mouse.moveTo(bufferQueuePos.x, bufferQueuePos.y)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(BufferQueueTooltip::class.java)

    // Thread tooltip
    // TODO: cell renderer has width=0 in this test, causing the in-cell tooltip switching logic to fail.

    // CPU core tooltip
    val coreTrackUi = FakeUi(trackGroups[1].overlay)
    coreTrackUi.mouse.moveTo(0, 0)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(CpuKernelTooltip::class.java)
  }

  @Test
  fun zoomToSelectionButton() {
    profilersView.studioProfilers.stage = stage
    val captureNode = CaptureNode(FakeCaptureNodeModel("Foo", "Bar", "123"))
    captureNode.startGlobal = 0
    captureNode.endGlobal = 10

    assertThat(profilersView.zoomToSelectionButton.isEnabled).isFalse()
    stage.multiSelectionModel.setSelection(captureNode, setOf(CaptureNodeAnalysisModel(captureNode, stage.capture, Utils::runOnUi)))
    assertThat(profilersView.zoomToSelectionButton.isEnabled).isTrue()
    assertThat(profilersView.stageView.stage.timeline.selectionRange.isSameAs(Range(0.0, 10.0))).isTrue()

    // Validate feature tracking
    val featureTracker = profilersView.studioProfilers.ideServices.featureTracker as FakeFeatureTracker
    featureTracker.resetZoomToSelectionCallCount()
    profilersView.zoomToSelectionButton.doClick()
    assertThat(featureTracker.zoomToSelectionCallCount).isEqualTo(1)
  }

  @Test
  fun zoomToSelectionButtonForTimelineEvent() {
    profilersView.studioProfilers.stage = stage
    val capture = Mockito.mock(SystemTraceCpuCapture::class.java).apply {
      whenever(range).thenReturn(Range(0.0, 50.0))
      whenever(frameRenderSequence).thenReturn { RenderSequence(null, null, null) }
    }
    val frame = AndroidFrameTimelineEvent(42, 42, 0, 20, 30, "",
                                          PerfettoTrace.FrameTimelineEvent.PresentType.PRESENT_LATE,
                                          PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED,
                                          false, false, 0)
    assertThat(profilersView.zoomToSelectionButton.isEnabled).isFalse()
    stage.multiSelectionModel.setSelection(frame, setOf(JankAnalysisModel(frame, capture, Utils::runOnUi)))
    assertThat(profilersView.zoomToSelectionButton.isEnabled).isTrue()
    assertThat(profilersView.stageView.stage.timeline.selectionRange.isSameAs(Range(0.0, 30.0))).isTrue()
  }

  @Test
  fun deselectAllLabel() {
    profilersView.studioProfilers.stage = stage
    val stageView = profilersView.stageView as CpuCaptureStageView
    val captureNode = CaptureNode(FakeCaptureNodeModel("Foo", "Bar", "123"))

    // Label should be visible when selection changes.
    assertThat(stageView.deselectAllToolbar.isVisible).isFalse()
    stage.multiSelectionModel.setSelection(captureNode, setOf(CaptureNodeAnalysisModel(captureNode, stage.capture, Utils::runOnUi)))
    assertThat(stageView.deselectAllToolbar.isVisible).isTrue()

    // Clicking the label should clear the selection.
    stageView.deselectAllLabel.doClick()
    assertThat(stage.multiSelectionModel.selections).isEmpty()
    assertThat(stageView.deselectAllToolbar.isVisible).isFalse()

    // Select a track and the clear the selection (b/228447505).
    stageView.trackGroupList.trackGroups[0].trackList.selectedIndex = 0
    assertThat(stage.multiSelectionModel.selections).isNotEmpty()
    stageView.deselectAllLabel.doClick()
    assertThat(stage.multiSelectionModel.selections).isEmpty()
    stageView.trackGroupList.trackGroups.forEach { assertThat(it.trackList.selectedIndices).isEmpty() }
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

    // Press A to pan left.
    // First select a small range.
    selectionRange.set(selectionRange.min + 100.0, selectionRange.min + 200.0)
    var oldRange = Range(selectionRange)
    ui.keyboard.press(FakeKeyboard.Key.A)
    ui.keyboard.release(FakeKeyboard.Key.A)
    assertThat(selectionRange.min).isLessThan(oldRange.min)
    assertThat(selectionRange.max).isLessThan(oldRange.max)

    // Press D to pan right.
    // First select a small range.
    selectionRange.set(selectionRange.min + 100.0, selectionRange.min + 200.0)
    oldRange = Range(selectionRange)
    ui.keyboard.press(FakeKeyboard.Key.D)
    ui.keyboard.release(FakeKeyboard.Key.D)
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
    ui.keyboard.press(FakeKeyboard.MENU_KEY_CODE)
    ui.mouse.wheel(0, 0, -1)
    ui.keyboard.release(FakeKeyboard.MENU_KEY_CODE)
    assertThat(selectionRange.length).isLessThan(rangeLength)

    // Ctrl/Cmd + wheel down to zoom out.
    rangeLength = selectionRange.length
    ui.keyboard.press(FakeKeyboard.MENU_KEY_CODE)
    ui.mouse.wheel(0, 0, 1)
    ui.keyboard.release(FakeKeyboard.MENU_KEY_CODE)
    assertThat(selectionRange.length).isGreaterThan(rangeLength)

    // Space + mouse drag to pan.
    // First select a partial trace.
    selectionRange.set(selectionRange.min + (selectionRange.max - selectionRange.min) / 2, selectionRange.max)
    val oldRange = Range(selectionRange)
    ui.keyboard.setFocus(stageView.trackGroupList.component)
    ui.keyboard.press(FakeKeyboard.Key.SPACE)
    ui.mouse.press(0, 0)
    // Pan right to shift the range left.
    ui.mouse.dragDelta(10, 0)
    try {
      ui.keyboard.release(FakeKeyboard.Key.SPACE)
    }
    catch (ignored: HeadlessException) {
      // JList#setDragEnabled doesn't support headless mode but it doesn't matter for this test so we can safely ignore it.
    }
    assertThat(selectionRange.min).isLessThan(oldRange.min)
    assertThat(selectionRange.max).isLessThan(oldRange.max)
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