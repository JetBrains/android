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
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.atrace.CpuFrameTooltip
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.JBSplitter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Point
import javax.swing.JLabel
import javax.swing.SwingUtilities

class CpuCaptureStageViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("FramesTest", cpuService, FakeTransportService(timer), FakeProfilerService(timer))
  private lateinit var stage: CpuCaptureStage
  private lateinit var profilersView: StudioProfilersView
  private val services = FakeIdeProfilerServices()

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.name), services, timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuCaptureStage.create(profilers, "", TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.VALID_TRACE_PATH))
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
    assertThat(stageView.trackGroupList.component.componentCount).isEqualTo(3) // track groups + tooltip component
    val treeWalker = TreeWalker(stageView.trackGroupList.component)

    val titleStrings = treeWalker.descendants().filterIsInstance<JLabel>().map(JLabel::getText).toList()
    assertThat(titleStrings).containsAllOf("Interaction", "Threads (3)").inOrder()
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
    val minimapOrigin = SwingUtilities.convertPoint(minimap, Point(0, 0), stageView.component)

    assertThat(stage.tooltip).isNull()
    // Move into minimap
    ui.mouse.moveTo(minimapOrigin.x, minimapOrigin.y)
    assertThat(stage.tooltip).isInstanceOf(CpuCaptureStageCpuUsageTooltip::class.java)
  }

  @Test
  fun showTrackGroupTooltip() {
    // Load Atrace
    services.enablePerfetto(true)
    val stage = CpuCaptureStage.create(profilersView.studioProfilers, "", TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.ATRACE_PID1_PATH))
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    stageView.component.setBounds(0, 0, 500, 500)
    val ui = FakeUi(stageView.component)
    val trackGroups = stageView.trackGroupList.trackGroups

    // Initial state
    assertThat(stageView.trackGroupList.activeTooltip).isNull()

    // Frame tooltip
    val frameTracks = trackGroups[1].trackList
    val frameTracksOrigin = SwingUtilities.convertPoint(frameTracks, Point(0, 0), stageView.component)
    ui.mouse.moveTo(frameTracksOrigin.x, frameTracksOrigin.y)
    assertThat(stageView.trackGroupList.activeTooltip).isInstanceOf(CpuFrameTooltip::class.java)

    // Thread tooltip
    // TODO: cell renderer has width=0 in this test, causing the in-cell tooltip switching logic to fail.

    // CPU core tooltip
    // TODO: use a trace with CPU cores data
  }
}