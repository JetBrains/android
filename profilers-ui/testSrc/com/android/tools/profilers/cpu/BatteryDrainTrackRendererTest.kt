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

import com.android.tools.adtui.AxisComponent
import com.android.tools.adtui.chart.linechart.LineChart
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.profilers.ProfilerTrackRendererType
import com.android.tools.profilers.cpu.systemtrace.BatteryDrainTrackModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryDrainTrackRendererTest {
  @Test
  fun render() {
    val batteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(BATTERY_DRAIN_COUNTERS, Range(), "foo"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()
    val component = BatteryDrainTrackRenderer().render(batteryDrainTrackModel)
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(AxisComponent::class.java)
    assertThat(component.components[1]).isInstanceOf(LineChart::class.java)
  }

  @Test
  fun axisLabelIsCorrect() {
    var percentBatteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(BATTERY_DRAIN_COUNTERS, Range(), "foo.pct"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()
    var formatter = percentBatteryDrainTrackModel.dataModel.axisComponentModel.formatter
    assertThat(formatter.getFormattedString(100.0, 100.0, true)).isEqualTo("100%")

    val microAmpHoursBatteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(BATTERY_DRAIN_COUNTERS, Range(), "foo.uah"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()
    formatter = microAmpHoursBatteryDrainTrackModel.dataModel.axisComponentModel.formatter
    assertThat(formatter.getFormattedString(100.0, 100.0, true)).isEqualTo("100 µah")

    val microAmpsBatteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(BATTERY_DRAIN_COUNTERS, Range(), "foo.ua"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()
    formatter = microAmpsBatteryDrainTrackModel.dataModel.axisComponentModel.formatter
    assertThat(formatter.getFormattedString(100.0, 100.0, true)).isEqualTo("100 µa")

    var noUnitBatteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(BATTERY_DRAIN_COUNTERS, Range(), "foo"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()
    formatter = noUnitBatteryDrainTrackModel.dataModel.axisComponentModel.formatter
    assertThat(formatter.getFormattedString(100.0, 100.0, true)).isEqualTo("100")
  }

  @Test
  fun batteryCurrentDrainNegativeMinRangeValuesGivesCorrectRange() {
    val batteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(NEGATIVE_MIN_DRAIN_COUNTERS, Range(), "ua"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()

    val component = BatteryDrainTrackRenderer().render(batteryDrainTrackModel)
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(AxisComponent::class.java)
    assertThat((component.components[0] as AxisComponent).model.range.min).isEqualTo(-1000.0)
    assertThat((component.components[0] as AxisComponent).model.range.max).isEqualTo(1000.0)
    assertThat((component.components[0] as AxisComponent).model.zero).isEqualTo(-1000.0)
  }

  @Test
  fun batteryCurrentDrainNegativeMinAndMaxRangeValuesGivesCorrectRange() {
    val batteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(NEGATIVE_MIN_AND_MAX_DRAIN_COUNTERS, Range(), "ua"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN,
      "Battery Drain"
    ).build()

    val component = BatteryDrainTrackRenderer().render(batteryDrainTrackModel)
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(AxisComponent::class.java)
    assertThat((component.components[0] as AxisComponent).model.range.min).isEqualTo(-1000.0)
    assertThat((component.components[0] as AxisComponent).model.range.max).isEqualTo(1000.0)
    assertThat((component.components[0] as AxisComponent).model.zero).isEqualTo(-1000.0)
  }

  @Test
  fun batteryCurrentDrainPositiveRangeValuesGivesCorrectRange() {
    val batteryDrainTrackModel = TrackModel.newBuilder(
      BatteryDrainTrackModel(BATTERY_DRAIN_COUNTERS, Range(), "ua"), ProfilerTrackRendererType.ANDROID_BATTERY_DRAIN, "Battery Drain"
    ).build()

    val component = BatteryDrainTrackRenderer().render(batteryDrainTrackModel)
    assertThat(component.componentCount).isEqualTo(2)
    assertThat(component.components[0]).isInstanceOf(AxisComponent::class.java)
    assertThat((component.components[0] as AxisComponent).model.range.min).isEqualTo(0.0)
    assertThat((component.components[0] as AxisComponent).model.range.max).isEqualTo(3000.0)
    assertThat((component.components[0] as AxisComponent).model.zero).isEqualTo(0.0)
  }

  companion object {
    private val BATTERY_DRAIN_COUNTERS = listOf(
      SeriesData(0L, 1000L),
      SeriesData(1000L, 2000L),
      SeriesData(2000L, 3000L)
    )

    private val NEGATIVE_MIN_DRAIN_COUNTERS = listOf(
      SeriesData(0L, -500L),
      SeriesData(1000L, 0L),
      SeriesData(2000L, 1000L),
    )

    private val NEGATIVE_MIN_AND_MAX_DRAIN_COUNTERS = listOf(
      SeriesData(0L, -1000L),
      SeriesData(1000L, -500L),
      SeriesData(2000L, -100L),
    )
  }
}