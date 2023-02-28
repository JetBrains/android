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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class CpuCaptureStageCpuUsageTooltipViewTest {
  private val timer = FakeTimer()

  private val myIdeServices = FakeIdeProfilerServices()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CaptureCpuUsageTooltipTest", FakeTransportService(timer))

  /**
   * For initializing [com.intellij.ide.HelpTooltip].
   */
  @get:Rule
  val appRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var captureStage: CpuCaptureStage
  private lateinit var tooltipView: FakeCaptureCpuUsageTooltipView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), myIdeServices, timer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
    captureStage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                          resolveWorkspacePath(CpuProfilerUITestUtils.VALID_TRACE_PATH).toFile(), 123L)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.stage = captureStage
    val stageView = profilersView.stageView as CpuCaptureStageView
    val tooltip = CpuCaptureStageCpuUsageTooltip(captureStage.minimapModel.cpuUsage,
                                                 captureStage.captureTimeline.tooltipRange)
    tooltipView = FakeCaptureCpuUsageTooltipView(stageView, tooltip)
  }

  @Test
  fun textUpdateOnTimeChange() {
    val tooltipTime = captureStage.captureTimeline.dataRange.min + TimeUnit.MILLISECONDS.toNanos(10)
    captureStage.captureTimeline.tooltipRange.set(tooltipTime, tooltipTime)
    assertThat(tooltipView.headingText).isEqualTo("00:10.000")
  }

  private class FakeCaptureCpuUsageTooltipView(parent: CpuCaptureStageView, tooltip: CpuCaptureStageCpuUsageTooltip)
    : CpuCaptureStageCpuUsageTooltipView(parent, tooltip) {
    init {
      createComponent()
    }
  }
}