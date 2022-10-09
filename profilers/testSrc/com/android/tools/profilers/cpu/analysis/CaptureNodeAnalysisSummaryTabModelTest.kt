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

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.Utils
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

class CaptureNodeAnalysisSummaryTabModelTest {
  @Test
  fun selectionRangeSingleNode() {
    val model = CaptureNodeAnalysisSummaryTabModel(Range(0.0, 100.0), Trace.TraceType.ART)
    val node = CaptureNode(SingleNameModel("Foo")).apply {
      startGlobal = 10
      endGlobal = 20
    }
    model.dataSeries.add(CaptureNodeAnalysisModel(node, Mockito.mock(CpuCapture::class.java), Utils::runOnUi))

    assertThat(model.selectionRange.isSameAs(Range(10.0, 20.0))).isTrue()
  }

  @Test
  fun selectionRangeMultipleNodes() {
    val model = CaptureNodeAnalysisSummaryTabModel(Range(0.0, 100.0), Trace.TraceType.ART)
    val nodes = listOf(
      CaptureNode(SingleNameModel("Foo")).apply {
        startGlobal = 10
        endGlobal = 20
      },
      CaptureNode(SingleNameModel("Bar")).apply {
        startGlobal = 15
        endGlobal = 30
      }
    )
    nodes.forEach { model.dataSeries.add(CaptureNodeAnalysisModel(it, Mockito.mock(CpuCapture::class.java), Utils::runOnUi)) }

    assertThat(model.selectionRange.isSameAs(Range(10.0, 30.0))).isTrue()
  }

  @Test
  fun labelForTraceType() {
    val range = Range(0.0, 1.0)
    assertThat(CaptureNodeAnalysisSummaryTabModel(range, Trace.TraceType.ART).label).isEqualTo("Stack Frame")
    assertThat(CaptureNodeAnalysisSummaryTabModel(range, Trace.TraceType.SIMPLEPERF).label).isEqualTo("Stack Frame")
    assertThat(CaptureNodeAnalysisSummaryTabModel(range, Trace.TraceType.ATRACE).label).isEqualTo("Trace Event")
    assertThat(CaptureNodeAnalysisSummaryTabModel(range, Trace.TraceType.PERFETTO).label).isEqualTo("Trace Event")
  }
}