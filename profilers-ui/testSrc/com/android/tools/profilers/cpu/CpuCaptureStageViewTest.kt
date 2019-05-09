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
  private lateinit var captureView: CpuCaptureStageView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.name), FakeIdeProfilerServices(), timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage = CpuCaptureStage(profilers,  0L)
    stage.studioProfilers.stage = stage
    captureView = profilersView.stageView as CpuCaptureStageView

  }

  @Test
  fun validateCaptureStageSetsCaptureView() {
    assertThat(profilersView.stageView).isInstanceOf(CpuCaptureStageView::class.java)
  }
}