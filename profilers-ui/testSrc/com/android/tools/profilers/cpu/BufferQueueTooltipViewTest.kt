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
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTooltip
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class BufferQueueTooltipViewTest {
  @Test
  fun textUpdatesOnRangeChanged() {
    val timeline = DefaultTimeline()
    val tooltip = BufferQueueTooltip(timeline, RangedSeries(timeline.dataRange, LazyDataSeries { BUFFER_QUEUE_VALUES }))
    val tooltipView = BufferQueueTooltipView(JPanel(), tooltip)

    timeline.dataRange.set(0.0, MICROS_IN_MILLI * 3.0)
    timeline.tooltipRange.set(1.0, 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    assertThat(tooltipView.valueLabel.text).isEqualTo("0 buffer(s) in SurfaceFlinger queue")

    timeline.tooltipRange.set(MICROS_IN_MILLI + 1.0, MICROS_IN_MILLI + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    assertThat(tooltipView.valueLabel.text).isEqualTo("1 buffer(s) in SurfaceFlinger queue")

    timeline.tooltipRange.set(MICROS_IN_MILLI * 2 + 1.0, MICROS_IN_MILLI * 2 + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.002")
    assertThat(tooltipView.valueLabel.text).isEqualTo("2 buffer(s) in SurfaceFlinger queue")
  }

  private companion object {
    val MICROS_IN_MILLI = TimeUnit.MILLISECONDS.toMicros(1)
    val BUFFER_QUEUE_VALUES = listOf(SeriesData(0, 0L), SeriesData(MICROS_IN_MILLI, 1L), SeriesData(MICROS_IN_MILLI * 2, 2L))
  }
}