/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.profilers.cpu.systemtrace.BatteryDrainTrackModel.Companion.getFormattedBatteryDrainName
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryDrainTrackModelTest {

  @Test
  fun rangeValuesComputedCorrectly() {
    val batteryDrainTrackModel = BatteryDrainTrackModel(BATTERY_DRAIN_VALUES, Range(0.0, 3000.0), "foo")

    assertThat(batteryDrainTrackModel.series.size).isEqualTo(1)
    assertThat(batteryDrainTrackModel.series[0].yRange.min).isEqualTo(0.0)
    assertThat(batteryDrainTrackModel.series[0].yRange.max).isEqualTo(3000.0)
  }

  @Test
  fun batteryDrainCounterNamesFormattedCorrectly() {
    val batteryDrainCounters = listOf(
      // Case 1: The following three predefined names should use the static mapping.
      // These names are predefined by perfetto.
      "batt.capacity_pct",
      "batt.charge_uah",
      "batt.current_ua",
      // Case 2: There is no static mapping, but the "batt." prefix is present.
      // Here, we remove the prefix and return the rest of the name.
      "batt.foo",
      // Case 3: There is no static mapping and the "batt." prefix is not present.
      // Here, we simply return the exact name back.
      "foo.bar"
    )

    val formattedBatteryDrainCounters = batteryDrainCounters.map { getFormattedBatteryDrainName(it) }
    assertThat(formattedBatteryDrainCounters).containsExactly("Capacity", "Charge", "Current", "foo", "foo.bar").inOrder()
  }

  companion object {
    private val BATTERY_DRAIN_VALUES = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 2000L),
      SeriesData(2000L, 3000L)
    )
  }
}