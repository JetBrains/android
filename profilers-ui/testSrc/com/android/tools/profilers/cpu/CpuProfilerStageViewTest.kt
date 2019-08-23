/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.adtui.RangeSelectionComponent
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.linechart.OverlayComponent
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerMode
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuProfilerStageView.KERNEL_VIEW_SPLITTER_RATIO
import com.android.tools.profilers.cpu.CpuProfilerStageView.SPLITTER_DEFAULT_RATIO
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.stacktrace.CodeLocation
import com.android.tools.profilers.stacktrace.ContextMenuItem
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.JBSplitter
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import java.awt.Graphics2D
import java.awt.Point
import java.io.File
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities

// Path to trace file. Used in test to build AtraceParser.
private const val TOOLTIP_TRACE_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"

@RunWith(Parameterized::class)
class CpuProfilerStageViewTest(newPipeline: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<Boolean> {
      return listOf(false, true)
    }
  }

  private val myTimer = FakeTimer()

  private val myComponents = FakeIdeProfilerComponents()

  private val myIdeServices = FakeIdeProfilerServices().apply {
    enableEventsPipeline(newPipeline)
  }

  private val myCpuService = FakeCpuService()

  private val myTransportService = FakeTransportService(myTimer)

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel(
    "CpuCaptureViewTestChannel", myCpuService, myTransportService, FakeProfilerService(myTimer),
    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build()
  )

  private lateinit var myStage: CpuProfilerStage

  private lateinit var myProfilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.name), myIdeServices, myTimer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)

    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    myStage.enter()
    myProfilersView = StudioProfilersView(profilers, myComponents)
  }

  @Ignore("b/113554750")
  @Test
  fun splitterRatioChangesOnAtraceCapture() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val splitter = TreeWalker(stageView.component).descendants().filterIsInstance<JBSplitter>().first()

    val traceFile = TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)
    val capture = AtraceParser(1).parse(traceFile, 0)

    assertThat(splitter.proportion).isWithin(0.0001f).of(SPLITTER_DEFAULT_RATIO)
    myStage.capture = capture
    // Verify the expanded kernel view adjust the splitter to take up more space.
    assertThat(splitter.proportion).isWithin(0.0001f).of(KERNEL_VIEW_SPLITTER_RATIO)
  }

  @Test
  fun testTooltipComponentIsFirstChild() {
    val cpuProfilerStageView = CpuProfilerStageView(myProfilersView, myStage)
    val treeWalker = TreeWalker(cpuProfilerStageView.component)
    val tooltipComponent = treeWalker.descendants().filterIsInstance(RangeTooltipComponent::class.java)[0]
    assertThat(tooltipComponent.parent.components[0]).isEqualTo(tooltipComponent)
  }

  @Test
  fun sparklineVisibilityChangesOnMouseStates() {
    val cpuProfilerStageView = CpuProfilerStageView(myProfilersView, myStage)
    cpuProfilerStageView.timeline.tooltipRange.set(0.0, 0.0)
    val treeWalker = TreeWalker(cpuProfilerStageView.component)
    // Grab the tooltip component and give it dimensions to be visible.
    val tooltipComponent = treeWalker.descendants().filterIsInstance<RangeTooltipComponent>()[0]
    tooltipComponent.setSize(10, 10)
    // Grab the overlay component and move the mouse to update the last position in the tooltip component.
    val overlayComponent = treeWalker.descendants().filterIsInstance<OverlayComponent>()[0]
    val overlayMouseUi = FakeUi(overlayComponent)
    overlayComponent.setBounds(1, 1, 10, 10)
    overlayMouseUi.mouse.moveTo(0, 0)
    // Grab the selection component and move the mouse to set the mode to !MOVE.
    val selectionComponent = treeWalker.descendants().filterIsInstance<RangeSelectionComponent>()[0]
    FakeUi(selectionComponent).mouse.moveTo(0, 0)
    val mockGraphics = Mockito.mock(Graphics2D::class.java)
    Mockito.`when`(mockGraphics.create()).thenReturn(mockGraphics)
    // Paint without letting the overlay component think we are over it.
    tooltipComponent.paint(mockGraphics)
    Mockito.verify(mockGraphics, Mockito.never()).draw(Mockito.any())
    // Enter the overlay component and paint again, this time we expect to draw the spark line.
    overlayMouseUi.mouse.moveTo(5, 5)
    tooltipComponent.paint(mockGraphics)
    Mockito.verify(mockGraphics, Mockito.times(1)).draw(Mockito.any())
    // Exit the overlay component and paint a third time. We don't expect our draw count to increase because the spark line
    // should not be drawn.
    overlayMouseUi.mouse.moveTo(0, 0)
    tooltipComponent.paint(mockGraphics)
    Mockito.verify(mockGraphics, Mockito.times(1)).draw(Mockito.any())
  }

  @Test
  fun importTraceModeShouldShowSelectedProcessName() {
    // Generates a capture
    myStage.profilerConfigModel.profilingConfiguration = FakeIdeProfilerServices.ATRACE_CONFIG
    CpuProfilerTestUtils.captureSuccessfully(myStage, myCpuService, myTransportService,
                                             CpuProfilerTestUtils.traceFileToByteString(
                                               TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)))

    // Enable import trace flag which is required for import-trace-mode.
    myIdeServices.enableImportTrace(true)
    myStage = CpuProfilerStage(myStage.studioProfilers, File("FakePathToTraceFile.trace"))
    myStage.enter()

    val cpuStageView = CpuProfilerStageView(myProfilersView, myStage)
    // Selecting the capture automatically selects the first process in the capture.
    myStage.setAndSelectCapture(0)
    val processLabel = TreeWalker(cpuStageView.toolbar).descendants().filterIsInstance<JLabel>()[0]
    // Verify the label is set properly in the toolbar.
    assertThat(processLabel.text).isEqualTo("Process: init")
  }

  @Test
  fun importTraceModeShouldShowCpuCaptureView() {
    // Enable import trace flag which is required for import-trace-mode.
    myIdeServices.enableImportTrace(true)
    myStage = CpuProfilerStage(myStage.studioProfilers, File("FakePathToTraceFile.trace"))
    myStage.enter()

    val cpuStageView = CpuProfilerStageView(myProfilersView, myStage)
    val treeWalker = TreeWalker(cpuStageView.component)
    // CpuStageView layout is a based on a splitter. The first component contains the usage chart, threads list, and kernel list. The second
    // component contains the CpuCaptureView and is set to null when it's not displayed.
    val captureViewComponent = treeWalker.descendants().filterIsInstance<JBSplitter>().first().secondComponent
    assertThat(captureViewComponent).isNotNull()
  }

  @Test
  fun recordButtonDisabledInDeadSessions() {
    // Create a valid capture and end the current session afterwards.
    myStage.profilerConfigModel.profilingConfiguration = FakeIdeProfilerServices.ATRACE_CONFIG
    CpuProfilerTestUtils.captureSuccessfully(
      myStage,
      myCpuService,
      myTransportService,
      CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)))
    val captureId = myStage.capture!!.traceId
    myStage.studioProfilers.sessionsManager.endCurrentSession()
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Re-create the stage so that the capture is not cached
    myStage = CpuProfilerStage(myStage.studioProfilers)
    myStage.studioProfilers.stage = myStage
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.toolbar).descendants().filterIsInstance<JButton>().first {
      it.text == CpuProfilerToolbar.RECORD_TEXT
    }
    // When creating the stage view, the record button should be disabled as the current session is dead.
    assertThat(recordButton.isEnabled).isFalse()

    // Set and select a capture, which will trigger capture parsing.
    val observer = AspectObserver()
    val parsingLatch = CpuProfilerTestUtils.waitForParsingStartFinish(myStage, observer)
    myStage.setAndSelectCapture(captureId)
    parsingLatch.await()

    // Even after parsing the capture, the record button should remain disabled.
    assertThat(recordButton.isEnabled).isFalse()
  }

  @Test
  fun stoppingAndStartingDisableRecordButton() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.toolbar).descendants().filterIsInstance<JButton>().first {
      it.text == CpuProfilerToolbar.RECORD_TEXT
    }

    myStage.captureState = CpuProfilerStage.CaptureState.STARTING
    // Setting the state to STARTING should disable the recording button
    assertThat(recordButton.isEnabled).isFalse()

    myStage.captureState = CpuProfilerStage.CaptureState.IDLE
    assertThat(recordButton.isEnabled).isTrue() // Check the record button is now enabled again

    myStage.captureState = CpuProfilerStage.CaptureState.STOPPING
    // Setting the state to STOPPING should disable the recording button
    assertThat(recordButton.isEnabled).isFalse()
  }

  @Test
  fun recordButtonShouldntHaveTooltip() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.toolbar).descendants().filterIsInstance<JButton>().first {
      it.text == CpuProfilerToolbar.RECORD_TEXT
    }
    assertThat(recordButton.toolTipText).isNull()

    myStage.captureState = CpuProfilerStage.CaptureState.CAPTURING
    val stopButton = TreeWalker(stageView.toolbar).descendants().filterIsInstance<JButton>().first {
      it.text == CpuProfilerToolbar.STOP_TEXT
    }
    assertThat(stopButton.toolTipText).isNull()
  }

  /**
   * Checks that the menu items common to all profilers are installed in the CPU profiler context menu.
   */
  @Test
  fun testCommonProfilersMenuItems() {
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    myComponents.clearContextMenuItems()
    // Create a CpuProfilerStageView. We don't need its value, so we don't store it in a variable.
    CpuProfilerStageView(myProfilersView, myStage)

    val expectedCommonMenus = listOf(StudioProfilersView.ATTACH_LIVE,
                                     StudioProfilersView.DETACH_LIVE,
                                     ContextMenuItem.SEPARATOR.text,
                                     StudioProfilersView.ZOOM_IN,
                                     StudioProfilersView.ZOOM_OUT)
    val items = myComponents.allContextMenuItems
    // CPU specific menus should be added.
    assertThat(items.size).isGreaterThan(expectedCommonMenus.size)

    val itemsSuffix = items.subList(items.size - expectedCommonMenus.size, items.size)
    assertThat(itemsSuffix.map { it.text }).containsExactlyElementsIn(expectedCommonMenus).inOrder()
  }

  @Test
  fun instructionsPanelIsTheFirstComponentOfUsageView() {
    val usageView = getUsageView(CpuProfilerStageView(myProfilersView, myStage))
    assertThat(usageView.getComponent(0)).isInstanceOf(InstructionsPanel::class.java)
  }

  @Test
  fun showsTooltipSeekComponentWhenMouseIsOverUsageView() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    val instructions = TreeWalker(stageView.component).descendants().filterIsInstance<InstructionsPanel>().first()
    instructions.isVisible = false // Hide instructions as they otherwise block the components we want to test

    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)

    val usageView = getUsageView(stageView)
    val usageViewPos = ui.getPosition(usageView)
    assertThat(usageViewPos.y).isGreaterThan(0)

    assertThat(stageView.shouldShowTooltipSeekComponent()).isFalse()
    // Move into |CpuUsageView|
    ui.mouse.moveTo(usageViewPos.x + usageView.width / 2, usageViewPos.y + usageView.height / 2)
    assertThat(stageView.shouldShowTooltipSeekComponent()).isTrue()

    // Moving the cursor over one of the selection handles should hide the tooltip seek bar
    val treeWalker = TreeWalker(stageView.component)
    val selection = treeWalker.descendants().filterIsInstance<RangeSelectionComponent>().first()
    val selectionPos = ui.getPosition(selection)

    val w = selection.width.toDouble()
    stageView.timeline.apply {
      viewRange.set(0.0, w)
      selectionRange.set(w / 2, w)
    }

    // One pixel to the left of the selection range targets the min handle
    ui.mouse.moveTo(selectionPos.x + selection.width / 2 - 1, selectionPos.y)
    assertThat(selection.mode).isEqualTo(RangeSelectionComponent.Mode.ADJUST_MIN)
    assertThat(stageView.shouldShowTooltipSeekComponent()).isFalse()
  }

  @Test
  fun tooltipIsUsageTooltipWhenMouseIsOverUsageView() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)

    val usageView = getUsageView(stageView)
    val usageViewOrigin = SwingUtilities.convertPoint(usageView, Point(0, 0), stageView.component)
    assertThat(usageViewOrigin.y).isGreaterThan(0)

    assertThat(myStage.tooltip).isNull()
    // Move into |CpuUsageView|
    ui.mouse.moveTo(usageViewOrigin.x, usageViewOrigin.y)
    assertThat(myStage.tooltip).isInstanceOf(CpuUsageTooltip::class.java)
  }

  @Test
  fun showsCpuCaptureViewWhenExpanded() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    assertThat(myStage.profilerMode).isEqualTo(ProfilerMode.NORMAL)
    // As we don't have an access to change the mode directly,
    // we're changing to expanded mode indirectly.
    myStage.capture = CpuProfilerUITestUtils.validCapture()
    assertThat(myStage.profilerMode).isEqualTo(ProfilerMode.EXPANDED)

    val splitter = TreeWalker(stageView.component).descendants().filterIsInstance<JBSplitter>().first()
    assertThat(splitter.secondComponent).isNotNull()
    assertThat(splitter.secondComponent.isVisible).isTrue()
  }

  @Test
  fun dontHideDetailsPanelWhenGoingBackToNormalMode() {
    myIdeServices.enableCpuNewRecordingWorkflow(false)

    val stageView = CpuProfilerStageView(myProfilersView, myStage)

    assertThat(myStage.profilerMode).isEqualTo(ProfilerMode.NORMAL)
    val splitter = TreeWalker(stageView.component).descendants().filterIsInstance<JBSplitter>().first()
    assertThat(splitter.secondComponent).isNull()

    // As we don't have an access to change the mode directly, we're changing it indirectly by setting a capture.
    myStage.capture = CpuProfilerUITestUtils.validCapture()
    assertThat(myStage.profilerMode).isEqualTo(ProfilerMode.EXPANDED)
    // CpuCaptureView should not be null now and should also be visible.
    assertThat(splitter.secondComponent).isNotNull()
    assertThat(splitter.secondComponent.isVisible).isTrue()

    // As we don't have an access to change the mode directly, we're changing it indirectly by simulating a code navigation.
    myStage.onNavigated(CodeLocation.stub())
    assertThat(myStage.profilerMode).isEqualTo(ProfilerMode.NORMAL)
    // Even though we went back to NORMAL (non maximized) mode so the user can view the code editor, we keep displaying the CpuCaptureView.
    assertThat(splitter.secondComponent).isNotNull()
    assertThat(splitter.secondComponent.isVisible).isTrue()
  }

  @Test
  fun showsCpuCaptureViewAlwaysOnNewRecordingWorkflow() {
    // We're not reusing |myStage| because we want to test the stage with the enabled feature flag.
    // Once the new recording workflow is stable and the flag is removed, we should remove the entire block.
    run {
      myIdeServices.enableCpuNewRecordingWorkflow(true)
      myStage = CpuProfilerStage(myStage.studioProfilers)
      myStage.enter()
    }
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    assertThat(myStage.profilerMode).isEqualTo(ProfilerMode.EXPANDED)

    val splitter = TreeWalker(stageView.component).descendants().filterIsInstance<JBSplitter>().first()
    assertThat(splitter.secondComponent).isNotNull()
  }

  private fun getUsageView(stageView: CpuProfilerStageView) = TreeWalker(stageView.component)
    .descendants()
    .filterIsInstance<CpuUsageView>()
    .first()
}