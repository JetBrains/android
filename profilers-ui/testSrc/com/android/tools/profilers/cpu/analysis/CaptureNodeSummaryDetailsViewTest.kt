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

import com.android.tools.adtui.StatLabel
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit
import javax.swing.JTable

class CaptureNodeSummaryDetailsViewTest {

  @get:Rule
  val grpcChannel = FakeGrpcChannel("CaptureNodeSummaryDetailsViewTest")

  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var profilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), FakeIdeProfilerServices())
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
  }

  @Test
  fun componentsArePopulated() {
    val captureNode = CaptureNode(SingleNameModel("Foo")).apply {
      startGlobal = TimeUnit.SECONDS.toMicros(10)
      endGlobal = TimeUnit.SECONDS.toMicros(20)
    }
    val model = CaptureNodeAnalysisSummaryTabModel(Range(0.0, Double.MAX_VALUE), Trace.UserOptions.TraceType.PERFETTO).apply {
      dataSeries.add(CaptureNodeAnalysisModel(captureNode, Mockito.mock(CpuCapture::class.java), Utils::runOnUi))
    }
    val view = CaptureNodeSummaryDetailsView(profilersView, model)
    val treeWalker = TreeWalker(view)
    assertThat(view.timeRangeLabel.text).isEqualTo("00:10.000 - 00:20.000")
    assertThat(view.dataTypeLabel.text).isEqualTo("Trace Event")
    // Selected node basic stats table and longest occurrences table.
    assertThat(treeWalker.descendants().filterIsInstance<JTable>().size).isEqualTo(2)
    // 5 stat labels: count, average, max, min, std.
    assertThat(treeWalker.descendants().filterIsInstance<StatLabel>().map { it.descText })
      .containsExactly("Count", "Average", "Max", "Min", "Std Dev")
  }
}