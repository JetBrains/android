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
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter
import com.android.tools.profilers.cpu.LazyDataSeries

/**
 * Track model for RSS (Resident Set Size) memory counter in CPU capture stage.
 */
class RssMemoryTrackModel(dataSeries: List<SeriesData<Long>>, viewRange: Range) : LineChartModel() {
  val memoryCounterSeries: RangedContinuousSeries
  val axisComponentModel: AxisComponentModel

  init {
    val maxValue = dataSeries.asSequence().map { it.value }.max() ?: 0
    val yRange = Range(0.0, maxValue.toDouble())
    axisComponentModel = ResizingAxisComponentModel.Builder(yRange, axisFormatter).build()
    memoryCounterSeries = RangedContinuousSeries(
      "RSS", viewRange, yRange, LazyDataSeries { dataSeries }
    )
    add(memoryCounterSeries)
  }

  /**
   * @property includedCountersNameMap a map of select memory counters, mapping counter name to display name.
   */
  companion object {
    private val axisFormatter = MemoryAxisFormatter(1, 2, 5)

    val includedCountersNameMap = sortedMapOf(
      "mem.rss" to "Total",
      "mem.rss.anon" to "Allocated",
      "mem.rss.file" to "File Mappings",
      "mem.rss.shmem" to "Shared"
    )
  }
}