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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.profilers.cpu.systemtrace.PowerRailTrackModel.Companion.isPowerRailShown
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PowerRailTrackModelTest {

  @Test
  fun nonZeroRangeValuesComputedCorrectlyWithDeltaAsPrimary() {
    val powerRailTrackModel = PowerRailTrackModel(
      PowerCounterData(NON_ZERO_RANGE_POWER_RAIL_DELTA_VALUES, NON_ZERO_RANGE_POWER_RAIL_CUMULATIVE_VALUES), Range(0.0, 3000.0),
      PowerProfilerDisplayMode.DELTA)

    assertThat(powerRailTrackModel.maxValue).isEqualTo(2000L)
    assertThat(powerRailTrackModel.minValue).isEqualTo(1000L)
    assertThat(powerRailTrackModel.baselineNormalizer).isEqualTo(250)
    assertThat(powerRailTrackModel.series.size).isEqualTo(1)
    assertThat(powerRailTrackModel.series[0].yRange.min).isEqualTo(750.0)
    assertThat(powerRailTrackModel.series[0].yRange.max).isEqualTo(2000.0)
  }

  @Test
  fun nonZeroRangeValuesComputedCorrectlyWithCumulativeAsPrimary() {
    val powerRailTrackModel = PowerRailTrackModel(
      PowerCounterData(NON_ZERO_RANGE_POWER_RAIL_DELTA_VALUES, NON_ZERO_RANGE_POWER_RAIL_CUMULATIVE_VALUES), Range(0.0, 3000.0),
      PowerProfilerDisplayMode.CUMULATIVE)

    assertThat(powerRailTrackModel.maxValue).isEqualTo(5000L)
    assertThat(powerRailTrackModel.minValue).isEqualTo(1000L)
    assertThat(powerRailTrackModel.baselineNormalizer).isEqualTo(1000)
    assertThat(powerRailTrackModel.series.size).isEqualTo(1)
    assertThat(powerRailTrackModel.series[0].yRange.min).isEqualTo(0.0)
    assertThat(powerRailTrackModel.series[0].yRange.max).isEqualTo(5000.0)
  }

  @Test
  fun zeroRangeValuesComputedCorrectly() {
    // Because we send in the same data for both delta and cumulative values in PowerCounterData, the choice of primary/secondary
    // does not matter. This is okay as the choice of primary/secondary is not relevant to this test.
    val powerRailTrackModel = PowerRailTrackModel(PowerCounterData(ZERO_RANGE_POWER_RAIL_VALUES, ZERO_RANGE_POWER_RAIL_VALUES),
                                                  Range(0.0, 3000.0), PowerProfilerDisplayMode.DELTA)

    assertThat(powerRailTrackModel.maxValue).isEqualTo(1000L)
    assertThat(powerRailTrackModel.minValue).isEqualTo(1000L)
    assertThat(powerRailTrackModel.baselineNormalizer).isEqualTo(0)
    assertThat(powerRailTrackModel.series.size).isEqualTo(1)
    assertThat(powerRailTrackModel.series[0].yRange.min).isEqualTo(1000.0)
    assertThat(powerRailTrackModel.series[0].yRange.max).isEqualTo(1000.0)
  }

  @Test
  fun hiddenPowerRailsDetected() {
    val powerRails = listOf(
      "foo",
      "power.rails.memory.interface",
      "power.rails.system.fabric",
      "power.L15M_VDD_SLC_M_uws",
      "power.S6M_LLDO1_uws",
      "power.S8M_LLDO2_uws",
    )

    val hiddenPowerRails = powerRails.filter { isPowerRailShown(it) }

    assertThat(hiddenPowerRails.size).isEqualTo(1)
    assertThat(hiddenPowerRails).containsAllIn(listOf("foo"))
  }

  companion object {
    private val NON_ZERO_RANGE_POWER_RAIL_CUMULATIVE_VALUES = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 2000L),
      SeriesData(2000L, 3000L),
      SeriesData(3000L, 5000L)
    )

    private val NON_ZERO_RANGE_POWER_RAIL_DELTA_VALUES = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 1000L),
      SeriesData(2000L, 1000L),
      SeriesData(3000L, 2000L)
    )

    private val ZERO_RANGE_POWER_RAIL_VALUES = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 1000L),
      SeriesData(2000L, 1000L)
    )
  }
}