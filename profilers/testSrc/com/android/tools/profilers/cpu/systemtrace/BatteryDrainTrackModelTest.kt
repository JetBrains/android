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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryDrainTrackModelTest {

  @Test
  fun rangeValuesComputedCorrectly() {
    val batteryDrainTrackModel = BatteryDrainTrackModel(BATTERY_DRAIN_VALUES, Range(0.0, 3000.0), "foo")

    assertThat(batteryDrainTrackModel.series.size).isEqualTo(1)
    assertThat(batteryDrainTrackModel.series[0].yRange.min).isEqualTo(1000.0)
    assertThat(batteryDrainTrackModel.series[0].yRange.max).isWithin(0.000001).of(3300.0)
  }

  companion object {
    private val BATTERY_DRAIN_VALUES = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 2000L),
      SeriesData(2000L, 3000L)
    )
  }
}