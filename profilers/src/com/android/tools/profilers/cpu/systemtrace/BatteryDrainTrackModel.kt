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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.LineChartModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.axis.AxisComponentModel
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter
import com.android.tools.profilers.cpu.LazyDataSeries

class BatteryDrainTrackModel(dataSeries: List<SeriesData<Long>>, viewRange: Range, trackName: String) : LineChartModel() {
  val batteryDrainCounterSeries: RangedContinuousSeries
  val axisComponentModel: AxisComponentModel

  init {
    val maxValue = dataSeries.asSequence().map { it.value }.maxOrNull() ?: 0
    val minValue = dataSeries.asSequence().map { it.value }.minOrNull() ?: 0

    val min = if (minValue != maxValue) minValue.toDouble() else 0.0

    val unit = getUnitFromTrackName(trackName)

    val axisFormatter = SingleUnitAxisFormatter(1, 2, 5, unit)

    // The 1.1 multiplier allows some breathing room for the topmost y-axis label to not be cut off.
    val yRange = Range(min, maxValue.toDouble() * 1.1)
    axisComponentModel = ResizingAxisComponentModel.Builder(yRange, axisFormatter).build()
    batteryDrainCounterSeries = RangedContinuousSeries(
      "Battery Drain", viewRange, yRange, LazyDataSeries { dataSeries }
    )
    add(batteryDrainCounterSeries)
  }

  companion object {
    private fun getUnitFromTrackName(trackName: String): String {
      return if (trackName.contains("pct")) "%"
      else if (trackName.contains("uah")) "µah"
      else if (trackName.contains("ua")) "µa"
      else ""
    }
  }
}