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
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerEvent
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTooltip
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class SurfaceflingerTooltipViewTest {
  @Test
  fun textUpdatesOnRangeChange() {
    val timeline = DefaultTimeline()
    val tooltip = SurfaceflingerTooltip(timeline, LazyDataSeries { SF_EVENTS })
    val tooltipView = SurfaceflingerTooltipView(JPanel(), tooltip)

    timeline.dataRange.set(0.0, MICROS_IN_MILLIS * 3.0)
    timeline.tooltipRange.set(1.0, 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    assertThat(tooltipView.eventNameLabel.isVisible).isFalse()
    assertThat(tooltipView.durationLabel.isVisible).isFalse()

    timeline.tooltipRange.set(MICROS_IN_MILLIS + 1.0, MICROS_IN_MILLIS + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    assertThat(tooltipView.eventNameLabel.isVisible).isTrue()
    assertThat(tooltipView.durationLabel.isVisible).isTrue()
    assertThat(tooltipView.eventNameLabel.text).isEqualTo("onMessageReceived")
    assertThat(tooltipView.durationLabel.text).isEqualTo("2 ms")
  }

  companion object {
    val MICROS_IN_MILLIS = TimeUnit.MILLISECONDS.toMicros(1)
    val SF_EVENTS = listOf(
      SeriesData(0, SurfaceflingerEvent(0, MICROS_IN_MILLIS, SurfaceflingerEvent.Type.IDLE)),
      SeriesData(MICROS_IN_MILLIS,
                 SurfaceflingerEvent(MICROS_IN_MILLIS, MICROS_IN_MILLIS * 3, SurfaceflingerEvent.Type.PROCESSING, "onMessageReceived"))
    )
  }
}