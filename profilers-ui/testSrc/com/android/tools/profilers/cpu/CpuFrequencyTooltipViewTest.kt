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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.systemtrace.CpuFrequencyTooltip
import com.google.common.truth.Truth
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class CpuFrequencyTooltipViewTest {

  @Test
  fun textUpdatesOnRangeChanged() {
    val timeline = DefaultTimeline()
    val tooltip = CpuFrequencyTooltip(timeline, 3, RangedSeries(timeline.dataRange, LazyDataSeries { CPU_FREQUENCY_VALUES }))
    val tooltipView = CpuFrequencyTooltipView(JPanel(), tooltip)

    timeline.dataRange.set(0.0, TimeUnit.MILLISECONDS.toMicros(3).toDouble())
    timeline.tooltipRange.set(0.0, 0.0)
    Truth.assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    Truth.assertThat(tooltipView.valueLabel.text).isEqualTo("CPU 3 Frequency: 0.0 kHz")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0)
    Truth.assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    Truth.assertThat(tooltipView.valueLabel.text).isEqualTo("CPU 3 Frequency: 1.5 MHz")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0)
    Truth.assertThat(tooltipView.headingText).isEqualTo("00:00.002")
    Truth.assertThat(tooltipView.valueLabel.text).isEqualTo("CPU 3 Frequency: 2.0 GHz")
  }

  private companion object {
    val CPU_FREQUENCY_VALUES = listOf(
      SeriesData(0, 0L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(1), 1500L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(2), 2 * 1000 * 1000L))
  }
}