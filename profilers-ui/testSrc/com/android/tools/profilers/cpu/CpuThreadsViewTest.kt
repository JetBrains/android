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
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.capturedetails.CaptureModel
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.ListSelectionModel

class CpuThreadsViewTest {
  private val cpuService = FakeCpuService()

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("CpuThreadsViewTest", cpuService, FakeProfilerService(),
                                    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private val timer = FakeTimer()
  private lateinit var stage: CpuProfilerStage
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
  }

  @Test
  fun selectedThreadReflectOnTheModel() {
    val threadsView = CpuThreadsView(stage)
    // Make a selection that includes all threads in the model.
    stage.studioProfilers.timeline.viewRange.set(-Double.MAX_VALUE, Double.MAX_VALUE)
    val threadsList = getThreadsList(threadsView)
    val tracker = ideServices.featureTracker as FakeFeatureTracker
    assertThat(threadsList.selectedValue).isNull()
    assertThat(stage.selectedThread).isEqualTo(CaptureModel.NO_THREAD)
    assertThat(tracker.isTrackSelectThreadCalled).isFalse()

    threadsList.selectedIndex = 0
    assertThat(threadsList.selectedValue).isNotNull()
    assertThat(stage.selectedThread).isEqualTo(threadsList.selectedValue.threadId)
    assertThat(tracker.isTrackSelectThreadCalled).isTrue()
  }

  @Test
  fun verifyTitleContent() {
    val threadsView = CpuThreadsView(stage)
    val title = TreeWalker(threadsView.component).descendants().filterIsInstance(JLabel::class.java).first().text
    // Text is actual an HTML, so we use contains instead of equals
    assertThat(title).contains("THREADS")
  }

  @Test
  fun scrollPaneViewportViewShouldBeThreadsView() {
    val threadsView = CpuThreadsView(stage)
    val descendants = TreeWalker(threadsView.component).descendants().filterIsInstance(CpuListScrollPane::class.java)
    assertThat(descendants).hasSize(1)
    val scrollPane = descendants[0]

    assertThat(scrollPane.viewport.view).isEqualTo(getThreadsList(threadsView))
  }

  @Test
  fun selectionModelShouldBeSingleSelection() {
    val threadsView = CpuThreadsView(stage)
    assertThat(getThreadsList(threadsView).selectionModel.selectionMode).isEqualTo(ListSelectionModel.SINGLE_SELECTION)
  }

  @Test
  fun threadsViewShouldHaveNullBorder() {
    val threadsView = CpuThreadsView(stage)
    assertThat(getThreadsList(threadsView).border).isNull()
  }

  @Test
  fun cellRendererShouldBeThreadCellRenderer() {
    val threadsView = CpuThreadsView(stage)
    assertThat(getThreadsList(threadsView).cellRenderer).isInstanceOf(ThreadCellRenderer::class.java)
  }

  @Test
  fun backgroundShouldBeDefaultStage() {
    val threadsView = CpuThreadsView(stage)
    assertThat(getThreadsList(threadsView).background).isEqualTo(ProfilerColors.DEFAULT_STAGE_BACKGROUND)
  }

  private fun getThreadsList(threadsView: CpuThreadsView) = TreeWalker(threadsView.component)
    .descendants()
    .filterIsInstance<DragAndDropList<CpuThreadsModel.RangedCpuThread>>()
    .first()
}