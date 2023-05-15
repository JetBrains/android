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
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.profilers.cpu.systemtrace.PowerRailTooltip
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class PowerRailTooltipViewTest {
  @Test
  fun textUpdatesOnRangeChangedWithDeltaAsPrimary() {
    val timeline = DefaultTimeline()
    val tooltip = PowerRailTooltip(timeline, "power.rail.foo", RangedSeries(timeline.dataRange, LazyDataSeries { POWER_RAIL_DELTA_VALUES }),
                                   RangedSeries(timeline.dataRange, LazyDataSeries { POWER_RAIL_CUMULATIVE_VALUES }),
                                   PowerProfilerDisplayMode.DELTA)
    val tooltipView = PowerRailTooltipView(JPanel(), tooltip)

    timeline.dataRange.set(0.0, TimeUnit.MILLISECONDS.toMicros(3).toDouble())
    timeline.tooltipRange.set(0.0, 0.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    /**
     * Expected formatting:
     *
     * power.rail.foo - Delta (µWs)                         0
     * power.rail.foo - Cumulative (µWs)                    0
     */
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Delta (µWs)")
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Cumulative (µWs)")
    // Make sure the Delta value comes first (because it is the primary value type).
    assertThat(tooltipView.valueLabel.text.indexOf("Delta") < tooltipView.valueLabel.text.indexOf("Cumulative")).isTrue()
    // Check to see if the values show up after each value title in the correct order.
    assertThat((tooltipView.valueLabel.text)).containsMatch("(µWs)[\\s\\S]*?0[\\s\\S]*?(µWs)[\\s\\S]*?0")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    /**
     * Expected formatting:
     *
     * power.rail.foo - Delta (µWs)                       100
     * power.rail.foo - Cumulative (µWs)                  100
     */
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Delta (µWs)")
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Cumulative (µWs)")
    // Make sure the Delta value comes first (because it is the primary value type).
    assertThat(tooltipView.valueLabel.text.indexOf("Delta") < tooltipView.valueLabel.text.indexOf("Cumulative")).isTrue()
    // Check to see if the values show up after each value title in the correct order.
    assertThat(tooltipView.valueLabel.text).containsMatch("(µWs)[\\s\\S]*?>100<[\\s\\S]*?(µWs)[\\s\\S]*?100")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.002")
    /**
     * Expected formatting:
     *
     * power.rail.foo - Delta (µWs)                       100
     * power.rail.foo - Cumulative (µWs)                  200
     */
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Delta (µWs)")
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Cumulative (µWs)")
    // Make sure the Delta value comes first (because it is the primary value type).
    assertThat(tooltipView.valueLabel.text.indexOf("Delta") < tooltipView.valueLabel.text.indexOf("Cumulative")).isTrue()
    // Check to see if the values show up after each value title in the correct order.
    assertThat(tooltipView.valueLabel.text).containsMatch("(µWs)[\\s\\S]*?100[\\s\\S]*?(µWs)[\\s\\S]*?200")
  }

  @Test
  fun textUpdatesOnRangeChangedWithCumulativeAsPrimary() {
    val timeline = DefaultTimeline()
    val tooltip = PowerRailTooltip(timeline, "power.rail.foo",
                                   RangedSeries(timeline.dataRange, LazyDataSeries { POWER_RAIL_CUMULATIVE_VALUES }),
                                   RangedSeries(timeline.dataRange, LazyDataSeries { POWER_RAIL_DELTA_VALUES }),
                                   PowerProfilerDisplayMode.CUMULATIVE)
    val tooltipView = PowerRailTooltipView(JPanel(), tooltip)

    timeline.dataRange.set(0.0, TimeUnit.MILLISECONDS.toMicros(3).toDouble())
    timeline.tooltipRange.set(0.0, 0.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.000")
    /**
     * Expected formatting:
     *
     * power.rail.foo - Cumulative (µWs)                    0
     * power.rail.foo - Delta (µWs)                         0
     */
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Cumulative (µWs)")
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Delta (µWs)")
    // Make sure the Cumulative value comes first (because it is the primary value type).
    assertThat(tooltipView.valueLabel.text.indexOf("Cumulative") < tooltipView.valueLabel.text.indexOf("Delta")).isTrue()
    // Check to see if the values show up after each value title in the correct order.
    assertThat(tooltipView.valueLabel.text).containsMatch("(µWs)[\\s\\S]*?>0<[\\s\\S]*?(µWs)[\\s\\S]*?>0<")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(1).toDouble() + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.001")
    /**
     * Expected formatting:
     *
     * power.rail.foo - Cumulative (µWs)                  100
     * power.rail.foo - Delta (µWs)                       100
     */
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Cumulative (µWs)")
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Delta (µWs)")
    // Make sure the Cumulative value comes first (because it is the primary value type).
    assertThat(tooltipView.valueLabel.text.indexOf("Cumulative") < tooltipView.valueLabel.text.indexOf("Delta")).isTrue()
    // Check to see if the values show up after each value title in the correct order.
    assertThat(tooltipView.valueLabel.text).containsMatch("(µWs)[\\s\\S]*?>100<[\\s\\S]*?(µWs)[\\s\\S]*?>100<")

    timeline.tooltipRange.set(TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0, TimeUnit.MILLISECONDS.toMicros(2).toDouble() + 1.0)
    assertThat(tooltipView.headingText).isEqualTo("00:00.002")
    /**
     * Expected formatting:
     *
     * power.rail.foo - Cumulative (µWs)                  200
     * power.rail.foo - Delta (µWs)                       100
     */
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Cumulative (µWs)")
    assertThat(tooltipView.valueLabel.text).contains("power.rail.foo - Delta (µWs)")
    // Make sure the Cumulative value comes first (because it is the primary value type).
    assertThat(tooltipView.valueLabel.text.indexOf("Cumulative") < tooltipView.valueLabel.text.indexOf("Delta")).isTrue()
    // Check to see if the values show up after each value title in the correct order.
    assertThat(tooltipView.valueLabel.text).containsMatch("(µWs)[\\s\\S]*?>200<[\\s\\S]*?(µWs)[\\s\\S]*?>100<")
  }

  private companion object {
    val POWER_RAIL_CUMULATIVE_VALUES = listOf(
      SeriesData(0, 0L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(1), 100L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(2), 200L))

    private val POWER_RAIL_DELTA_VALUES = listOf(
      SeriesData(0, 0L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(1), 100L),
      SeriesData(TimeUnit.MILLISECONDS.toMicros(2), 100L))
  }
}