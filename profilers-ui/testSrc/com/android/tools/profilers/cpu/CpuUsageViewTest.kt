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
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuUsageViewTest {
  private val cpuService = FakeCpuService()
  private val timer = FakeTimer()

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("CpuUsageNormalModeViewTest", cpuService,
                                    FakeTransportService(timer), FakeProfilerService(timer),
                                    FakeMemoryService(), FakeEventService())

  private lateinit var stage: CpuProfilerStage
  private lateinit var ideServices: FakeIdeProfilerServices

  @Before
  fun setUp() {
    ideServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
  }

  @Test
  fun shouldNotShowInstructionsPanel() {
    val usageView = CpuUsageView(stage)
    Truth.assertThat(TreeWalker(usageView).descendants().filterIsInstance(InstructionsPanel::class.java)).hasSize(0)
  }

  @Test
  fun showsUsageChart() {
    val usageView = CpuUsageView(stage)
    Truth.assertThat(TreeWalker(usageView).descendants().filterIsInstance(LineChart::class.java)).hasSize(1)
  }
}