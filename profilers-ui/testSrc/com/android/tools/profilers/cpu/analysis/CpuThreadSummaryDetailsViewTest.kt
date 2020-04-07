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

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.CpuThreadTrackModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class CpuThreadSummaryDetailsViewTest {
  companion object {
    private val CAPTURE_RANGE = Range(0.0, Double.MAX_VALUE)
  }

  @Test
  fun componentsArePopulated() {
    val timeline = DefaultTimeline().apply {
      viewRange.set(TimeUnit.MILLISECONDS.toMicros(100).toDouble(), TimeUnit.MILLISECONDS.toMicros(200).toDouble())
    }
    val cpuThreadTrackModel = CpuThreadTrackModel(
      Mockito.mock(CpuCapture::class.java),
      CpuThreadInfo(123, "foo"),
      timeline,
      MultiSelectionModel())
    val model = CpuThreadAnalysisSummaryTabModel(CAPTURE_RANGE, timeline.viewRange).apply {
      dataSeries.add(cpuThreadTrackModel)
    }
    val view = CpuThreadSummaryDetailsView(JPanel(), model)

    assertThat(view.timeRangeLabel.text).isEqualTo("00.100 - 00.200")
    assertThat(view.durationLabel.text).isEqualTo("100 ms")
    assertThat(view.dataTypeLabel.text).isEqualTo("Thread")
    assertThat(view.threadIdLabel.text).isEqualTo("123")
  }

  @Test
  fun rangeChangeUpdatesLabels() {
    val timeline = DefaultTimeline().apply {
      viewRange.set(0.0, 0.0)
    }
    val cpuThreadTrackModel = CpuThreadTrackModel(
      Mockito.mock(CpuCapture::class.java),
      CpuThreadInfo(123, "foo"),
      timeline,
      MultiSelectionModel())
    val model = CpuThreadAnalysisSummaryTabModel(CAPTURE_RANGE, timeline.viewRange).apply {
      dataSeries.add(cpuThreadTrackModel)
    }
    val view = CpuThreadSummaryDetailsView(JPanel(), model)

    assertThat(view.timeRangeLabel.text).isEqualTo("00.000 - 00.000")
    assertThat(view.durationLabel.text).isEqualTo("0 μs")

    timeline.viewRange.set(TimeUnit.SECONDS.toMicros(1).toDouble(), TimeUnit.SECONDS.toMicros(2).toDouble())
    assertThat(view.timeRangeLabel.text).isEqualTo("01.000 - 02.000")
    assertThat(view.durationLabel.text).isEqualTo("1 s")
  }
}