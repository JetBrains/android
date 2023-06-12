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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class BatteryDrainTooltipViewTest {
  @Test
  fun textUpdatesOnRangeChanged() {
    val timeline = DefaultTimeline()
    val tooltip = BatteryDrainTooltip(timeline, "batt.foo", RangedSeries(timeline.dataRange, LazyDataSeries { BATTERY_DRAIN_VALUES }))
    val tooltipView = BatteryDrainTooltipView(JPanel(), tooltip)

    timeline.dataRange.set(0.0, TimeUnit.MILLISECONDS.toMicros(3).toDouble())
    timeline.tooltipRange.set(0.0, 0.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    assertThat(tooltipView.descriptionLabel.text).contains("[PLACEHOLDER BATTERY DESCRIPTION]")
    assertThat(tooltipView.valueLabel.text).endsWith("batt.foo: 0")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    assertThat(tooltipView.valueLabel.text).endsWith("batt.foo: 100")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.002")
    assertThat(tooltipView.valueLabel.text).endsWith("batt.foo: 200")
  }

  private companion object {
    val BATTERY_DRAIN_VALUES = listOf(
      SeriesData(0, 0L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(1), 100L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(2), 200L))
  }
}