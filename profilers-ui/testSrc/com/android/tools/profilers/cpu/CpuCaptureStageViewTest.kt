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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuCaptureStageViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("FramesTest", cpuService, FakeTransportService(timer), FakeProfilerService(timer))
  private lateinit var stage: CpuCaptureStage
  private lateinit var profilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.name), FakeIdeProfilerServices(), timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuCaptureStage.create(profilers, "", TestUtils.getWorkspaceFile(CpuProfilerUITestUtils.VALID_TRACE_PATH))
  }

  @Test
  fun validateCaptureStageSetsCaptureView() {
    stage.studioProfilers.stage = stage
    assertThat(profilersView.stageView).isInstanceOf(CpuCaptureStageView::class.java)
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
    assertThat(stageView.trackGroupList.model.size).isEqualTo(2)
    val interactionTrackGroup = stageView.trackGroupList.model.getElementAt(0)
    assertThat(interactionTrackGroup.title).isEqualTo("Interaction")
    assertThat(interactionTrackGroup.size).isEqualTo(2)
    assertThat(interactionTrackGroup.get(0).title).isEqualTo("User")
    assertThat(interactionTrackGroup.get(1).title).isEqualTo("Lifecycle")

    val threadsTrackGroup = stageView.trackGroupList.model.getElementAt(1)
    assertThat(threadsTrackGroup.title).isEqualTo("Threads (3)")
    assertThat(threadsTrackGroup.size).isEqualTo(3)
  }

  @Test
  fun analysisPanelIsInitializedAfterParsing() {
    val stageView = CpuCaptureStageView(profilersView, stage)
    stage.enter()
    assertThat(stageView.analysisPanel.component.size).isNotSameAs(0)
  }
}