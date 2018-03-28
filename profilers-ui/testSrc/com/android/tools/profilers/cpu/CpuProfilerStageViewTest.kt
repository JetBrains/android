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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.CpuProfilerStageView.KERNEL_VIEW_SPLITTER_RATIO
import com.android.tools.profilers.cpu.CpuProfilerStageView.SPLITTER_DEFAULT_RATIO
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.android.tools.profilers.stacktrace.ContextMenuItem
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.ExpandedItemListCellRendererWrapper
import com.intellij.ui.JBSplitter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JList

// Path to trace file. Used in test to build AtraceParser.
private const val TOOLTIP_TRACE_DATA_FILE = "tools/adt/idea/profilers-ui/testData/cputraces/atrace.ctrace"

class CpuProfilerStageViewTest {

  private val myProfilerService = FakeProfilerService()

  private val myComponents = FakeIdeProfilerComponents()

  private val myIdeServices = FakeIdeProfilerServices()

  private val myCpuService = FakeCpuService()

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel(
      "CpuCaptureViewTestChannel", myCpuService, myProfilerService,
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build()
  )

  private lateinit var myStage: CpuProfilerStage

  private lateinit var myProfilersView: StudioProfilersView

  @Before
  fun setUp() {
    val timer = FakeTimer()
    val profilers = StudioProfilers(myGrpcChannel.client, myIdeServices, timer)
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    myProfilersView = StudioProfilersView(profilers, myComponents)
  }

  @Test
  fun contextMenuShouldBeInstalled() {
    // Enable the export trace flag
    myIdeServices.enableExportTrace(true)
    // Clear any context menu items added to the service to make sure we'll have only the items created in CpuProfilerStageView
    myComponents.clearContextMenuItems()
    // Create a CpuProfilerStageView. We don't need its value, so we don't store it in a variable.
    CpuProfilerStageView(myProfilersView, myStage)

    var items = myComponents.allContextMenuItems
    assertThat(items).hasSize(12)

    // Check we add CPU specific actions first.
    assertThat(items[0].text).isEqualTo("Record CPU trace")
    assertThat(items[1]).isEqualTo(ContextMenuItem.SEPARATOR)

    assertThat(items[2].text).isEqualTo("Export trace...")
    assertThat(items[3]).isEqualTo(ContextMenuItem.SEPARATOR)

    assertThat(items[4].text).isEqualTo("Next capture")
    assertThat(items[5].text).isEqualTo("Previous capture")
    assertThat(items[6]).isEqualTo(ContextMenuItem.SEPARATOR)

    // Check the common menu items are added only after the "export trace" action
    checkCommonProfilersMenuItems(items, 7)

    // Disable the export trace flag
    myIdeServices.enableExportTrace(false)
    myComponents.clearContextMenuItems()
    CpuProfilerStageView(myProfilersView, myStage)

    items = myComponents.allContextMenuItems
    assertThat(items).hasSize(10)

    assertThat(items[0].text).isEqualTo("Record CPU trace")
    assertThat(items[1]).isEqualTo(ContextMenuItem.SEPARATOR)

    assertThat(items[2].text).isEqualTo("Next capture")
    assertThat(items[3].text).isEqualTo("Previous capture")
    assertThat(items[4]).isEqualTo(ContextMenuItem.SEPARATOR)
    // Check the common menu items are added after "Record" action
    checkCommonProfilersMenuItems(items, 5)
  }

  @Test
  fun testCpuCellRendererHasSessionPid() {
    // Create
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(1234).setState(Common.Process.State.ALIVE).build()
    // Create a session and a ongoing profiling session.
    myStage.studioProfilers.sessionsManager.endCurrentSession()
    myStage.studioProfilers.sessionsManager.beginSession(device, process1)
    val session = myStage.studioProfilers.sessionsManager.selectedSession
    val cpuProfilerStageView = CpuProfilerStageView(myProfilersView, myStage)
    val treeWalker = TreeWalker(cpuProfilerStageView.component)
    val cpuTree = treeWalker.descendants().filterIsInstance<JList<CpuKernelModel.CpuState>>().first()
    // JBList wraps cellRenderer in a ExpandedItemListCellRendererWrapper, so we get this and unwrap our instance.
    assertThat(cpuTree.cellRenderer).isInstanceOf(ExpandedItemListCellRendererWrapper::class.java)
    assertThat((cpuTree.cellRenderer as ExpandedItemListCellRendererWrapper<CpuKernelModel.CpuState>).wrappee).isInstanceOf(
        CpuKernelCellRenderer::class.java
    )
    val cpuCell = (cpuTree.cellRenderer as ExpandedItemListCellRendererWrapper<CpuKernelModel.CpuState>).wrappee as CpuKernelCellRenderer

    // Validate that the process we are looking at is the same as the process from the session.
    assertThat(cpuCell.myProcessId).isEqualTo(session.pid)
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

  /**
   * Checks that the menu items common to all profilers are installed in the CPU profiler context menu.
   * They should be at the bottom of the context menu, starting at a given index.
   */
  private fun checkCommonProfilersMenuItems(items: List<ContextMenuItem>, startIndex: Int) {
    var index = startIndex
    assertThat(items[index++].text).isEqualTo("Attach to Live")
    assertThat(items[index++].text).isEqualTo("Detach from Live")
    assertThat(items[index++]).isEqualTo(ContextMenuItem.SEPARATOR)
    assertThat(items[index++].text).isEqualTo("Zoom in")
    assertThat(items[index].text).isEqualTo("Zoom out")
  }
}