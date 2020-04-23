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
import com.android.tools.profilers.cpu.CpuCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class FullTraceSummaryDetailsViewTest {
  companion object {
    private val CAPTURE_RANGE = Range(0.0, Double.MAX_VALUE)
  }

  @Test
  fun componentsArePopulated() {
    val selectionRange = Range(TimeUnit.SECONDS.toMicros(1).toDouble(), TimeUnit.SECONDS.toMicros(60).toDouble())
    val model = FullTraceAnalysisSummaryTabModel(CAPTURE_RANGE, selectionRange).apply {
      dataSeries.add(Mockito.mock(CpuCapture::class.java))
    }
    val view = FullTraceSummaryDetailsView(JPanel(), model)

    assertThat(view.timeRangeLabel.text).isEqualTo("01.000 - 01:00.000")
    assertThat(view.durationLabel.text).isEqualTo("59 s")
  }

  @Test
  fun rangeChangeUpdatesLabels() {
    val selectionRange = Range(0.0, 0.0)
    val model = FullTraceAnalysisSummaryTabModel(CAPTURE_RANGE, selectionRange).apply {
      dataSeries.add(Mockito.mock(CpuCapture::class.java))
    }
    val view = FullTraceSummaryDetailsView(JPanel(), model)

    assertThat(view.timeRangeLabel.text).isEqualTo("00.000 - 00.000")
    assertThat(view.durationLabel.text).isEqualTo("0 μs")

    selectionRange.set(TimeUnit.MILLISECONDS.toMicros(1).toDouble(), TimeUnit.MILLISECONDS.toMicros(2).toDouble())
    assertThat(view.timeRangeLabel.text).isEqualTo("00.001 - 00.002")
    assertThat(view.durationLabel.text).isEqualTo("1 ms")
  }
}