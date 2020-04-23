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
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class CaptureNodeSummaryDetailsViewTest {
  @Test
  fun componentsArePopulated() {
    val captureNode = CaptureNode(SingleNameModel("Foo")).apply {
      startGlobal = TimeUnit.SECONDS.toMicros(10)
      endGlobal = TimeUnit.SECONDS.toMicros(20)
    }
    val model = CaptureNodeAnalysisSummaryTabModel(Range(0.0, Double.MAX_VALUE)).apply {
      dataSeries.add(CaptureNodeAnalysisModel(captureNode, Mockito.mock(CpuCapture::class.java)))
    }
    val view = CaptureNodeSummaryDetailsView(JPanel(), model)
    assertThat(view.timeRangeLabel.text).isEqualTo("10.000 - 20.000")
    assertThat(view.durationLabel.text).isEqualTo("10 s")
    assertThat(view.dataTypeLabel.text).isEqualTo("Trace Event")
  }
}