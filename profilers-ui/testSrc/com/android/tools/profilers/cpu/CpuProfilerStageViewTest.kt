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
import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.SelectionComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.linechart.OverlayComponent
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.*
import com.android.tools.profilers.FakeProfilerService.FAKE_DEVICE_NAME
import com.android.tools.profilers.FakeProfilerService.FAKE_PROCESS_NAME
import com.android.tools.profilers.cpu.CpuProfilerStageView.KERNEL_VIEW_SPLITTER_RATIO
import com.android.tools.profilers.cpu.CpuProfilerStageView.SPLITTER_DEFAULT_RATIO
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.stacktrace.ContextMenuItem
import com.google.common.collect.Iterators
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.JBSplitter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Graphics2D
import java.awt.Point
import java.io.File
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.SwingUtilities

// Path to trace file. Used in test to build AtraceParser.
private const val TOOLTIP_TRACE_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"

private const val ATRACE_MISSING_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace_processid_1.ctrace"

private const val ART_TRACE_FILE = "tools/adt/idea/profilers-ui/testData/valid_trace.trace"

class CpuProfilerStageViewTest {

  private val myProfilerService = FakeProfilerService()

  private val myComponents = FakeIdeProfilerComponents()

  private val myIdeServices = FakeIdeProfilerServices()

  private val myCpuService = FakeCpuService()

  private val myTimer = FakeTimer()

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel(
      "CpuCaptureViewTestChannel", myCpuService, myProfilerService,
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build()
  )

  private lateinit var myStage: CpuProfilerStage

  private lateinit var myProfilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(myGrpcChannel.client, myIdeServices, myTimer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)

    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    myStage.enter()
    myProfilersView = StudioProfilersView(profilers, myComponents)
  }

  @Test
  fun testCpuKernelViewIsExpandedOnAtraceCapture() {
    // Create default device and process for a default session.
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(1234).setState(Common.Process.State.ALIVE).build()
    // Create a session and a ongoing profiling session.
    myStage.studioProfilers.sessionsManager.endCurrentSession()
    myStage.studioProfilers.sessionsManager.beginSession(device, process1)
    val cpuProfilerStageView = CpuProfilerStageView(myProfilersView, myStage)
    val treeWalker = TreeWalker(cpuProfilerStageView.component)
    // Find our cpu list.
    val cpuList = treeWalker.descendants().filterIsInstance<JList<CpuKernelModel.CpuState>>().first()
    val hideablePanel = TreeWalker(cpuList).ancestors().filterIsInstance<HideablePanel>().first()
    // The panel containing the cpu list should be hidden and collapsed by default.
    assertThat(hideablePanel.isExpanded).isFalse()
    assertThat(hideablePanel.isVisible).isFalse()
    val traceFile = TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)
    val capture = AtraceParser(1).parse(traceFile, 0)
    myStage.capture = capture
    // After we set a capture it should be visible and expanded.
    assertThat(hideablePanel.isExpanded).isTrue()
    assertThat(hideablePanel.isVisible).isTrue()

    // Verify the expanded kernel view adjust the splitter to take up more space.
    val splitter = treeWalker.descendants().filterIsInstance<JBSplitter>().first()
    assertThat(splitter.proportion).isWithin(0.0001f).of(KERNEL_VIEW_SPLITTER_RATIO)

    // Verify when we reset the capture our splitter value goes back to default.
    myStage.capture = null
    assertThat(splitter.proportion).isWithin(0.0001f).of(SPLITTER_DEFAULT_RATIO)
  }

  @Test
  fun testHideablePanelsHaveItemCountsAsTitle() {
    myStage.studioProfilers.stage = myStage
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)))
    myStage.setAndSelectCapture(0)
    // One second is enough for the models to be updated.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myStage.studioProfilers.timeline.viewRange.set(myStage.capture!!.range)

    val cpuProfilerStageView = myProfilersView.stageView as CpuProfilerStageView
    val treeWalker = TreeWalker(cpuProfilerStageView.component)
    // Find our cpu list.
    val cpuList = treeWalker.descendants().filterIsInstance<JList<CpuKernelModel.CpuState>>().first()
    var hideablePanel = TreeWalker(cpuList).ancestors().filterIsInstance<HideablePanel>().first()
    var panelTitle = TreeWalker(hideablePanel).descendants().filterIsInstance<JLabel>().first()
    assertThat(panelTitle.text).contains("KERNEL (4)")
    // Find our thread list.
    val threadsList = treeWalker.descendants().filterIsInstance<JList<CpuThreadsModel.RangedCpuThread>>().last()
    hideablePanel = TreeWalker(threadsList).ancestors().filterIsInstance<HideablePanel>().first()
    panelTitle = TreeWalker(hideablePanel).descendants().filterIsInstance<JLabel>().first()
    assertThat(panelTitle.text).contains("THREADS (0)")
    // Add a thread
    myCpuService.addAdditionalThreads(1, "Test", mutableListOf(
      CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder().setTimestamp(0).setNewState(
        CpuProfiler.GetThreadsResponse.State.SLEEPING).build()))
    // Update the view range triggering an aspect change in CpuThreadsModel.
    myStage.studioProfilers.timeline.viewRange.set(myStage.studioProfilers.timeline.dataRange)
    // Tick to trigger
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(panelTitle.text).contains("THREADS (1)")
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
    overlayMouseUi.mouse.moveTo(0,0)
    // Grab the selection component and move the mouse to set the mode to !MOVE.
    val selectionComponent = treeWalker.descendants().filterIsInstance<SelectionComponent>()[0]
    FakeUi(selectionComponent).mouse.moveTo(0,0)
    val mockGraphics = Mockito.mock(Graphics2D::class.java)
    Mockito.`when`(mockGraphics.create()).thenReturn(mockGraphics)
    // Paint without letting the overlay component think we are over it.
    tooltipComponent.paint(mockGraphics)
    Mockito.verify(mockGraphics, Mockito.never()).draw(Mockito.any())
    // Enter the overlay component and paint again, this time we expect to draw the spark line.
    overlayMouseUi.mouse.moveTo(5,5)
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
    // Enable import trace and sessions view, both of which are required for import-trace-mode.
    myIdeServices.enableImportTrace(true)
    myIdeServices.enableSessionsView(true)
    myStage = CpuProfilerStage(myStage.studioProfilers, File("FakePathToTraceFile.trace"))
    myStage.enter()
    // Set a capture of type atrace.
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)))
    val cpuStageView = CpuProfilerStageView(myProfilersView, myStage)
    // Selecting the capture automatically selects the first process in the capture.
    myStage.setAndSelectCapture(0)
    val processLabel = TreeWalker(cpuStageView.toolbar).descendants().filterIsInstance<JLabel>()[0]
    // Verify the label is set properly in the toolbar.
    assertThat(processLabel.text).isEqualTo("Process: init")
  }

  @Test
  fun traceMissingDataShowsDialog() {
    // Set a capture of type atrace.
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(TOOLTIP_TRACE_DATA_FILE)))
    val cpuStageView = CpuProfilerStageView(myProfilersView, myStage)
    // Select valid capture no dialog should be presented.
    myStage.setAndSelectCapture(0)
    assertThat(myIdeServices.balloonTitle).isNull()
    assertThat(myIdeServices.balloonBody).isNull()
    // Select invalid capture we should see dialog.
    myCpuService.setTrace(CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(ATRACE_MISSING_DATA_FILE)))
    myStage.setAndSelectCapture(1)
    assertThat(myIdeServices.balloonTitle).isEqualTo(CpuProfilerStageView.ATRACE_BUFFER_OVERFLOW_TITLE)
    assertThat(myIdeServices.balloonBody).isEqualTo(CpuProfilerStageView.ATRACE_BUFFER_OVERFLOW_MESSAGE)
  }

  @Test
  fun recordButtonDisabledInDeadSessions() {
    // Create a valid capture and end the current session afterwards.
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ART
    myCpuService.setGetTraceResponseStatus(CpuProfiler.GetTraceResponse.Status.SUCCESS)
    myCpuService.setTrace(ByteString.copyFrom(TestUtils.getWorkspaceFile(ART_TRACE_FILE).readBytes()))
    myStage.studioProfilers.sessionsManager.endCurrentSession()

    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.toolbar).descendants().filterIsInstance<JButton>().first {
      it.text == CpuProfilerStageView.RECORD_TEXT
    }
    // When creating the stage view, the record button should be disabled as the current session is dead.
    assertThat(recordButton.isEnabled).isFalse()

    var transitionHappened = false
    // Listen to CAPTURE_STATE changes and check if the new state is equal to what we expect.
    val captureStates = Iterators.forArray(CpuProfilerStage.CaptureState.PARSING,
                                           CpuProfilerStage.CaptureState.IDLE)
    val observer = AspectObserver()
    myStage.aspect.addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_STATE) {
      assertThat(myStage.captureState).isEqualTo(captureStates.next())
      transitionHappened = true
    }

    // Set and select a capture, which will trigger a transition of states. First PARSING then IDLE.
    myStage.setAndSelectCapture(FakeCpuService.FAKE_TRACE_ID)

    assertThat(transitionHappened).isTrue() // Sanity check to verify we actually changed states.

    // Even after parsing the capture, the record button should remain disabled.
    assertThat(recordButton.isEnabled).isFalse()
  }

  @Test
  fun stoppingAndStartingDisableRecordButton() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    val recordButton = TreeWalker(stageView.toolbar).descendants().filterIsInstance<JButton>().first {
      it.text == CpuProfilerStageView.RECORD_TEXT
    }
    // Capture Status label is the last child component of the tooltip, which is also the parent of the record button. If this test starts
    // to fail after a layout change, you will probably need to change the component index to get the capture status label from the toolbar.
    val captureStatus = recordButton.parent.getComponent(recordButton.parent.componentCount - 1) as JLabel
    captureStatus.text = "any dummy text"

    myStage.captureState = CpuProfilerStage.CaptureState.STARTING
    // Setting the state to STARTING should disable the recording button and clear the text of the capture status label
    assertThat(recordButton.isEnabled).isFalse()
    assertThat(captureStatus.text).isEmpty()

    myStage.captureState = CpuProfilerStage.CaptureState.IDLE
    assertThat(recordButton.isEnabled).isTrue() // Check the record button is now enabled again
    captureStatus.text = "any dummy text"

    myStage.captureState = CpuProfilerStage.CaptureState.STOPPING
    // Setting the state to STOPPING should disable the recording button and clear the text of the capture status label
    assertThat(recordButton.isEnabled).isFalse()
    assertThat(captureStatus.text).isEmpty()
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
    stageView.component.setBounds(0, 0, 500, 500)

    val usageView = getUsageView(stageView)
    val usageViewOrigin = SwingUtilities.convertPoint(usageView, Point(0, 0), stageView.component)

    val ui = FakeUi(stageView.component)

    assertThat(stageView.showTooltipSeekComponent()).isFalse()
    // Move into |CpuUsageView|
    ui.mouse.moveTo(usageViewOrigin.x + usageView.width / 2, usageViewOrigin.y + usageView.height / 2)
    assertThat(stageView.showTooltipSeekComponent()).isTrue()

    // Grab the selection component and move the mouse to set the mode to ADJUST_MIN.
    val treeWalker = TreeWalker(stageView.component)
    val selectionComponent = treeWalker.descendants().filterIsInstance<SelectionComponent>()[0]
    FakeUi(selectionComponent).mouse.moveTo(0,0)
    ui.mouse.moveTo(0, 0)
    assertThat(stageView.showTooltipSeekComponent()).isFalse()
  }

  @Test
  fun tooltipIsUsageTooltipWhenMouseIsOverUsageView() {
    val stageView = CpuProfilerStageView(myProfilersView, myStage)
    stageView.component.setBounds(0, 0, 500, 500)

    val usageView = getUsageView(stageView)
    val usageViewOrigin = SwingUtilities.convertPoint(usageView, Point(0, 0), stageView.component)
    val ui = FakeUi(stageView.component)

    assertThat(myStage.tooltip).isNull()
    // Move into |CpuUsageView|
    ui.mouse.moveTo(usageViewOrigin.x + usageView.width / 2, usageViewOrigin.y + usageView.height / 2)
    assertThat(myStage.tooltip).isInstanceOf(CpuUsageTooltip::class.java)
  }

  private fun getUsageView(stageView: CpuProfilerStageView) = TreeWalker(stageView.component)
    .descendants()
    .filterIsInstance<CpuUsageView>()
    .first()
}