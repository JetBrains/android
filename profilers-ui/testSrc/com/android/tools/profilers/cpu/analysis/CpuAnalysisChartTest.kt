/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CpuAnalysisChartTest {
  private val timer = FakeTimer()

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CPuAnalysisChartTest", FakeTransportService(timer))

  /**
   * For initializing [com.intellij.ide.HelpTooltip].
   */
  @get:Rule
  val appRule = ApplicationRule()

  private lateinit var profilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices(), timer)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())

  }

  @Test
  fun filterIsApplied() {
    val capture = CpuProfilerUITestUtils.validCapture()
    val model = CpuAnalysisChartModel<CpuCapture>(CpuAnalysisTabModel.Type.TOP_DOWN, Range(capture.range), capture,
                                                  { capture.captureNodes },
                                                  Utils::runOnUi)
    model.dataSeries.add(capture)
    val chart = CpuAnalysisChart(profilersView, model)

    // Apply text filter "main". The main thread node should be an exact match.
    chart.filterComponent.model.filter = Filter("main")
    assertThat(capture.captureNodes.filter { node -> node.filterType == CaptureNode.FilterType.EXACT_MATCH }).isNotEmpty()
  }
}
