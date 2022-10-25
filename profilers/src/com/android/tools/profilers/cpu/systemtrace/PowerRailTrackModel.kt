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
import com.android.tools.profilers.cpu.LazyDataSeries
import java.util.function.Supplier

/**
 * Track model for Power counter in CPU capture stage.
 */
class PowerRailTrackModel(dataSeries: List<SeriesData<Long>>, viewRange: Range) : LineChartModel() {
  val maxValue = dataSeries.asSequence().map { it.value }.maxOrNull() ?: 0
  val minValue = dataSeries.asSequence().map { it.value }.minOrNull() ?: 0

  // Instead of using the min value as the bottom of the range and thus not show the lowest value,
  // we can bring the range down by a value relative to the range of data. Experimentally, 1/4 of
  // the range works well to represent the data, but this is likely going to change.
  val baselineNormalizer = (maxValue - minValue) / 4

  val powerRailCounterSeries = RangedContinuousSeries("Power Rails", viewRange,
                                                      Range(minValue.toDouble() - baselineNormalizer, maxValue.toDouble()),
                                                      LazyDataSeries(Supplier { dataSeries }))
  init {
    add(powerRailCounterSeries)
  }
}