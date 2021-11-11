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

import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.profilers.cpu.LazyDataSeries
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame.FrameThread
import java.util.concurrent.TimeUnit

class FrameState(threadType: FrameThread, systemTraceData: CpuSystemTraceData, range: Range) {
  val series: DataSeries<SystemTraceFrame>
  val model: StateChartModel<SystemTraceFrame> = StateChartModel()
  val vsyncSeries: RangedSeries<Long>

  companion object {
    /**
     * The default value such that any frame taking longer than this value will be marked as bad.
     */
    //TODO (b/74404740): Make this configurable.
    val slowFrameRateUs = TimeUnit.MILLISECONDS.toMicros(17)
  }

  init {
    series = LazyDataSeries { systemTraceData.getFrames(threadType) }
    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    model.addSeries(RangedSeries(range, series))
    vsyncSeries = RangedSeries(range, LazyDataSeries { systemTraceData.vsyncCounterValues })
  }
}