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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.*
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.ExpandedItemListCellRendererWrapper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JPanel

// TODO(b/110767935): add tests for handling mouse events (e.g. selecting a kernel slice or toggling the panel expanded state)
class CpuKernelsViewTest {
  private val cpuService = FakeCpuService()

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("CpuUsageImportModeViewTest", cpuService, FakeProfilerService(),
                                    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private val timer = FakeTimer()
  private lateinit var stage: CpuProfilerStage
  private lateinit var threadsView: CpuThreadsView
  private lateinit var ideServices: FakeIdeProfilerServices

  @Before
  fun setUp() {
    ideServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(grpcChannel.client, ideServices, timer)
    profilers.setPreferredProcess(FakeProfilerService.FAKE_DEVICE_NAME, FakeProfilerService.FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    stage.enter()

    threadsView = CpuThreadsView(stage, JPanel())
  }

  @Test
  fun cpuCellRendererHasSessionPid() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(1234).setState(Common.Process.State.ALIVE).build()
    // Create a session and a ongoing profiling session.
    stage.studioProfilers.sessionsManager.endCurrentSession()
    stage.studioProfilers.sessionsManager.beginSession(device, process1)
    val session = stage.studioProfilers.sessionsManager.selectedSession
    val kernelsView = CpuKernelsView(stage, threadsView, JPanel())
    // JBList wraps cellRenderer in a ExpandedItemListCellRendererWrapper, so we get this and unwrap our instance.
    assertThat(kernelsView.cellRenderer).isInstanceOf(ExpandedItemListCellRendererWrapper::class.java)
    val cellRenderer = kernelsView.cellRenderer as ExpandedItemListCellRendererWrapper
    assertThat(cellRenderer.wrappee).isInstanceOf(CpuKernelCellRenderer::class.java)

    // Validate that the process we are looking at is the same as the process from the session.
    assertThat((cellRenderer.wrappee as CpuKernelCellRenderer).myProcessId).isEqualTo(session.pid)
  }

  @Test
  fun backgroundShouldBeDefaultStage() {
    val kernelsView = CpuKernelsView(stage, threadsView, JPanel())
    assertThat(kernelsView.background).isEqualTo(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
  }

  @Test
  fun panelShouldBeHiddenByDefault() {
    val kernelsView = CpuKernelsView(stage, threadsView, JPanel())
    assertThat(kernelsView.panel.isVisible).isFalse()
  }

  @Test
  fun verifyTitleContent() {
    val kernelsView = CpuKernelsView(stage, threadsView, JPanel())

    val title = TreeWalker(kernelsView.panel).descendants().filterIsInstance(JLabel::class.java).first().text
    // Text is actual an HTML, so we use contains instead of equals
    assertThat(title).contains("KERNEL")
  }

  @Test
  fun scrollPaneViewportViewShouldBeKernelsView() {
    val kernelsView = CpuKernelsView(stage, threadsView, JPanel())
    val descendants = TreeWalker(kernelsView.panel).descendants().filterIsInstance(CpuListScrollPane::class.java)
    assertThat(descendants).hasSize(1)
    val scrollPane = descendants[0]

    assertThat(scrollPane.viewport.view).isEqualTo(kernelsView)
  }
}