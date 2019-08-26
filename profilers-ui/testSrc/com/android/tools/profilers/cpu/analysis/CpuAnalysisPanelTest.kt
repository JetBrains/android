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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.android.tools.profilers.cpu.FakeCpuService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.Thread.sleep

class CpuAnalysisPanelTest {

  private val timer = FakeTimer()
  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", FakeCpuService(), FakeProfilerService(timer))
  private val profilerClient = ProfilerClient(grpcChannel.name)
  private lateinit var profilers: StudioProfilers
  private val services = FakeIdeProfilerServices()

  @Before
  fun setUp() {
    profilers = StudioProfilers(profilerClient, services, timer)
  }

  @Test
  fun tabsUpdatedOnCaptureCompleted() {
    services.enablePerfetto(true)
    services.enableAtrace(true)
    sleep(1000)
    val stage = CpuCaptureStage.create(profilers, "Test", File(CpuProfilerUITestUtils.ATRACE_TRACE_PATH))
    val panel = CpuAnalysisPanel(stage)
    assertThat(panel.tabView.tabCount).isEqualTo(0)
    stage.enter()
    assertThat(panel.tabView.tabCount).isNotEqualTo(0)
  }

  @Test
  fun newAnalysisIsAutoSelected() {
    services.enablePerfetto(true)
    services.enableAtrace(true)
    val stage = CpuCaptureStage.create(profilers, "Test", File(CpuProfilerUITestUtils.ATRACE_TRACE_PATH))
    val panel = CpuAnalysisPanel(stage)
    stage.enter()
    val selectionModel = CpuAnalysisModel("TEST")
    selectionModel.tabs.add(CpuAnalysisTabModel<CpuCapture>(CpuAnalysisTabModel.Type.SUMMARY))
    selectionModel.tabs.add(CpuAnalysisTabModel<CpuCapture>(CpuAnalysisTabModel.Type.SUMMARY))
    stage.addCpuAnalysisModel(selectionModel)
    assertThat(panel.tabView.tabCount).isEqualTo(2)
  }
}