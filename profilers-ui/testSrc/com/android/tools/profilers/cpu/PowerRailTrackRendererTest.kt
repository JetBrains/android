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

import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.cpu.systemtrace.PowerRailTrackModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PowerRailTrackRendererTest {
  @Test
  fun render() {
    val powerRailTrackModel = TrackModel.newBuilder(
      PowerRailTrackModel(POWER_RAIL_COUNTERS, Range()), ProfilerTrackRendererType.ANDROID_POWER_RAIL, "Power Rails"
    ).build()
    val component = PowerRailTrackRenderer().render(powerRailTrackModel)
    assertThat(component.componentCount).isEqualTo(1)
    assertThat(component.components[0]).isInstanceOf(LineChart::class.java)
  }

  companion object {
    private val POWER_RAIL_COUNTERS = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 2000L),
      SeriesData(2000L, 3000L)
    )
  }
}