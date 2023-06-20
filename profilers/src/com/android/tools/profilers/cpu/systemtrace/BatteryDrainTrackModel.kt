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
import com.android.tools.adtui.model.formatter.PercentAxisFormatter
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter
import com.android.tools.profilers.cpu.LazyDataSeries
import kotlin.math.abs

class BatteryDrainTrackModel(dataSeries: List<SeriesData<Long>>, viewRange: Range, trackName: String) : LineChartModel() {
  val batteryDrainCounterSeries: RangedContinuousSeries
  val axisComponentModel: AxisComponentModel

  init {
    val maxValue = dataSeries.asSequence().map { it.value }.maxOrNull() ?: 0
    val minValue = dataSeries.asSequence().map { it.value }.minOrNull() ?: 0

    val unit = getUnitFromTrackName(trackName)
    val negValuePresent = minValue < 0 || maxValue < 0

    val axisFormatter = when (unit) {
      "%" -> PercentAxisFormatter(1, 2)
      "µah" -> SingleUnitAxisFormatter(1, 5, 1, unit)
      // If a negative value is present, we limit the number of major axis ticks to keep the label only the 0 axis label.
      "µa" -> if (negValuePresent) SingleUnitAxisFormatter(1, 2, 1, unit) else SingleUnitAxisFormatter(1, 5, 1, unit)
      else -> SingleUnitAxisFormatter(1, 2, 5, unit)
    }

    val absLargestValue = abs(minValue.toDouble()).coerceAtLeast(abs(maxValue.toDouble()))
    val yRange = when (unit) {
      // We use the range of [-max, max] if there is a negative value present to coerce the 0 axis label (clarifies negative values).
      "µa" -> if (negValuePresent) Range(-absLargestValue, absLargestValue) else Range(0.0, maxValue.toDouble())
      else -> Range(0.0, maxValue.toDouble())
    }

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