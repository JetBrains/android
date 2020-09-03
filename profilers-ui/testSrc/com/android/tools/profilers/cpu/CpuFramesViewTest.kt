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
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.ExpandedItemListCellRendererWrapper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JList

// TODO(b/110767935): add tests for handling mouse events (e.g. selecting a frame slice or toggling the panel expanded state)
class CpuFramesViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("FramesTest", cpuService, FakeTransportService(timer), FakeProfilerService(timer))
  private lateinit var stage: CpuProfilerStage

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    stage.enter()
  }

  @Test
  fun framesViewHasCpuFramesCellRenderer() {
    val framesView = CpuFramesView(stage)
    // JBList wraps cellRenderer in a ExpandedItemListCellRendererWrapper, so we get this and unwrap our instance.
    val frames = TreeWalker(framesView.component).descendants().filterIsInstance(JList::class.java).first()
    assertThat(frames.cellRenderer).isInstanceOf(ExpandedItemListCellRendererWrapper::class.java)
    val cellRenderer = frames.cellRenderer as ExpandedItemListCellRendererWrapper
    assertThat(cellRenderer.wrappee).isInstanceOf(CpuFramesCellRenderer::class.java)
  }

  @Test
  fun backgroundShouldBeDefaultStage() {
    val framesView = CpuFramesView(stage)
    val frames = TreeWalker(framesView.component).descendants().filterIsInstance(JList::class.java).first()
    assertThat(frames.background).isEqualTo(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
  }

  @Test
  fun framesVisibileRowsIsTwo() {
    val framesView = CpuFramesView(stage)
    val frames = TreeWalker(framesView.component).descendants().filterIsInstance(JList::class.java).first()
    assertThat(frames.visibleRowCount).isEqualTo(2)
  }

  @Test
  fun expandedOnAtrace() {
    val framesView = CpuFramesView(stage)
    val hideablePanel = TreeWalker(framesView.component).descendants().filterIsInstance<HideablePanel>().first()

    val traceFile = TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.ATRACE_TRACE_PATH)
    val capture = AtraceParser(MainProcessSelector(idHint = 1)).parse(traceFile, 0)

    assertThat(framesView.component.isVisible).isFalse()
    assertThat(hideablePanel.isExpanded).isFalse()
    stage.capture = capture
    // After we set a capture it should be visible and expanded.
    assertThat(framesView.component.isVisible).isTrue()
    assertThat(hideablePanel.isExpanded).isTrue()
  }

  @Test
  fun panelShouldBeHiddenByDefault() {
    val framesView = CpuFramesView(stage)
    assertThat(framesView.component.isVisible).isFalse()
  }

  @Test
  fun verifyTitleContent() {
    val framesView = CpuFramesView(stage)
    val title = TreeWalker(framesView.component).descendants().filterIsInstance(JLabel::class.java).first().text
    // Text is actual an HTML, so we use contains instead of equals
    assertThat(title).contains("FRAMES")
  }

  @Test
  fun scrollPaneViewportViewShouldBeFramesView() {
    val framesView = CpuFramesView(stage)
    val descendants = TreeWalker(framesView.component).descendants().filterIsInstance(CpuListScrollPane::class.java)
    assertThat(descendants).hasSize(1)
    val scrollPane = descendants[0]

    val frames = TreeWalker(framesView.component).descendants().filterIsInstance(JList::class.java).first()
    assertThat(scrollPane.viewport.view).isEqualTo(frames)
  }
}