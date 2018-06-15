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

import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests [CpuUsageView.ImportModeView]
 */
class CpuUsageImportModeViewTest {
  private val cpuService = FakeCpuService()

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("CpuUsageImportModeViewTest", cpuService, FakeProfilerService(),
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

    // Enable import trace and sessions view, both of which are required for import-trace-mode.
    ideServices.enableImportTrace(true)
    ideServices.enableSessionsView(true)

    stage = CpuProfilerStage(profilers, File("FakePathToTraceFile.trace"))
    stage.studioProfilers.stage = stage
    stage.enter()
  }

  @Test
  fun showsInstructionsPanel() {
    val usageView = CpuUsageView.ImportModeView(stage)

    val panelList = TreeWalker(usageView).descendants().filterIsInstance(InstructionsPanel::class.java)
    // We cannot get the string due to privacy of InstructionsComponent.
    // This panel is the panel that appears in the Cpu Usage area indicating we have no cpu usage data.
    Truth.assertThat(panelList).hasSize(1)
    Truth.assertThat(panelList[0].getRenderInstructionsForComponent(0)).hasSize(1)
  }

  @Test
  fun showsUsageChart() {
    val usageView = CpuUsageView.ImportModeView(stage)
    Truth.assertThat(TreeWalker(usageView).descendants().filterIsInstance(LineChart::class.java)).hasSize(1)
  }

  @Test
  fun shouldNotShowLegends() {
    val usageView = CpuUsageView.ImportModeView(stage)
    Truth.assertThat(TreeWalker(usageView).descendants().filterIsInstance(LegendComponent::class.java)).hasSize(0)
  }
}