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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.android.tools.profilers.cpu.systemtrace.CpuKernelModel
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.ExpandedItemListCellRendererWrapper
import com.intellij.ui.components.JBList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel

// TODO(b/110767935): add tests for handling mouse events (e.g. selecting a kernel slice or toggling the panel expanded state)
class CpuKernelsViewTest {
  private val timer = FakeTimer()
  private val cpuService = FakeCpuService()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("CpuKernelsViewTest", cpuService,
                                    transportService, FakeProfilerService(timer),
                                    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private lateinit var stage: CpuProfilerStage
  private lateinit var threadsView: CpuThreadsView
  private lateinit var ideServices: FakeIdeProfilerServices

  @Before
  fun setUp() {
    ideServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    stage.enter()

    threadsView = CpuThreadsView(stage)
  }

  @Test
  fun cpuCellRendererHasSessionPid() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(1234).setState(Common.Process.State.ALIVE).build()
    // Create a session and a ongoing profiling session.
    stage.studioProfilers.sessionsManager.endCurrentSession()
    stage.studioProfilers.sessionsManager.beginSession(device, process1)
    val session = stage.studioProfilers.sessionsManager.selectedSession
    val kernelsView = CpuKernelsView(stage)
    val kernels = getKernelsList(kernelsView)
    // JBList wraps cellRenderer in a ExpandedItemListCellRendererWrapper, so we get this and unwrap our instance.
    assertThat(kernels.cellRenderer).isInstanceOf(ExpandedItemListCellRendererWrapper::class.java)
    val cellRenderer = kernels.cellRenderer as ExpandedItemListCellRendererWrapper
    assertThat(cellRenderer.wrappee).isInstanceOf(CpuKernelCellRenderer::class.java)

    // Validate that the process we are looking at is the same as the process from the session.
    assertThat((cellRenderer.wrappee as CpuKernelCellRenderer).myProcessId).isEqualTo(session.pid)
  }

  @Test
  fun backgroundShouldBeDefaultStage() {
    val kernelsView = CpuKernelsView(stage)
    assertThat(getKernelsList(kernelsView).background).isEqualTo(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
  }

  @Test
  fun panelShouldBeHiddenByDefault() {
    val kernelsView = CpuKernelsView(stage)
    assertThat(kernelsView.component.isVisible).isFalse()
  }

  @Test
  fun testHideablePanelsHaveItemCountsAsTitle() {
    val kernelsView = CpuKernelsView(stage)

    stage.studioProfilers.stage = stage
    stage.profilerConfigModel.profilingConfiguration = FakeIdeProfilerServices.ATRACE_CONFIG
    CpuProfilerTestUtils.captureSuccessfully(
      stage,
      cpuService,
      transportService,
      CpuProfilerTestUtils.traceFileToByteString(TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.ATRACE_TRACE_PATH)))
    stage.timeline.viewRange.set(stage.capture!!.range)

    val hideablePanel = TreeWalker(kernelsView.component).ancestors().filterIsInstance<HideablePanel>().first()
    TreeWalker(hideablePanel).descendants().filterIsInstance<JLabel>().first().let { panel ->
      assertThat(panel.text).contains("KERNEL (4)")
    }
  }

  @Test
  fun expandedOnAtraceCapture() {
    val kernelsView = CpuKernelsView(stage)
    val hideablePanel = TreeWalker(kernelsView.component).descendants().filterIsInstance<HideablePanel>().first()

    val traceFile = TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.ATRACE_TRACE_PATH)
    val capture = AtraceParser(MainProcessSelector(idHint = 1)).parse(traceFile, 0)

    assertThat(kernelsView.component.isVisible).isFalse()
    assertThat(hideablePanel.isExpanded).isFalse()
    stage.capture = capture
    // After we set a capture it should be visible and expanded.
    assertThat(kernelsView.component.isVisible).isTrue()
    assertThat(hideablePanel.isExpanded).isTrue()
  }

  @Test
  fun verifyTitleContent() {
    val kernelsView = CpuKernelsView(stage)

    val title = TreeWalker(kernelsView.component).descendants().filterIsInstance(JLabel::class.java).first().text
    // Text is actual an HTML, so we use contains instead of equals
    assertThat(title).contains("KERNEL")
  }

  @Test
  fun scrollPaneViewportViewShouldBeKernelsView() {
    val kernelsView = CpuKernelsView(stage)
    val descendants = TreeWalker(kernelsView.component).descendants().filterIsInstance(CpuListScrollPane::class.java)
    assertThat(descendants).hasSize(1)
    val scrollPane = descendants[0]

    assertThat(scrollPane.viewport.view).isEqualTo(getKernelsList(kernelsView))
  }

  private fun getKernelsList(kernelsView: CpuKernelsView) = TreeWalker(kernelsView.component)
    .descendants()
    .filterIsInstance<JBList<CpuKernelModel.CpuState>>()
    .first()
}