/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEvent
import com.android.tools.profilers.cpu.systemtrace.AndroidFrameEventTooltip
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class AndroidFrameEventTooltipViewTest {
  @Test
  fun testUpdatesOnRangeChange() {
    val timeline = DefaultTimeline().apply {
      dataRange.set(0.0, MICROS_TO_MILLIS * 3.0)
      viewRange.set(0.0, MICROS_TO_MILLIS * 3.0)
    }
    val tooltip = AndroidFrameEventTooltip(timeline, RangedSeries(timeline.viewRange, LazyDataSeries { FRAME_EVENTS }))
    val tooltipView = AndroidFrameEventTooltipView(JPanel(), tooltip)

    timeline.tooltipRange.set(1.0, 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    assertThat(tooltipView.labelContainer.isVisible).isFalse()

    timeline.tooltipRange.set(MICROS_TO_MILLIS + 1.0, MICROS_TO_MILLIS + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    assertThat(tooltipView.labelContainer.isVisible).isTrue()
    assertThat(tooltipView.frameNumberLabel.text).isEqualTo("Frame number: 123")
    assertThat(tooltipView.startTimeLabel.text).isEqualTo("Start time: 00:00.001")
    assertThat(tooltipView.durationLabel.text).isEqualTo("Duration: 2 ms")
  }

  private companion object {
    val MICROS_TO_MILLIS = TimeUnit.MILLISECONDS.toMicros(1)
    val FRAME_EVENTS = listOf<SeriesData<AndroidFrameEvent>>(
      SeriesData(0, AndroidFrameEvent.Padding),
      SeriesData(MICROS_TO_MILLIS, AndroidFrameEvent.Data(123, 1000, 2000)))
  }
}