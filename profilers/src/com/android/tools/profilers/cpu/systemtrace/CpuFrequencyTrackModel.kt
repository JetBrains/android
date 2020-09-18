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
 * Track model for CPU frequency counter in CPU capture stage.
 */
class CpuFrequencyTrackModel(dataSeries: List<SeriesData<Long>>, viewRange: Range) : LineChartModel() {
  val cpuFrequencySeries = RangedContinuousSeries("CPU Frequency", viewRange, Range(0.0, MAX_FREQ_KHZ),
                                                  LazyDataSeries(Supplier { dataSeries }))

  init {
    add(cpuFrequencySeries)
  }

  companion object {
    private const val MAX_FREQ_KHZ = 3500000.0
  }
}