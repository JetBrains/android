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

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.profilers.cpu.LazyDataSeries

class JankyFrameModel(layers: List<List<AndroidFrameTimelineEvent>>,
                      vsyncs: List<SeriesData<Long>>,
                      viewRange: Range): StateChartModel<AndroidFrameTimelineEvent?>() {
  val vsyncSeries = RangedSeries(viewRange, LazyDataSeries { vsyncs })

  init {
    layers.forEach { frames ->
      val paddedJankyFrames = frames
        .filter { it.actualEndUs > it.expectedEndUs }
        .padded({ it.expectedStartUs }, { it.actualEndUs }, { it }, { _, _ -> null })
      addSeries(RangedSeries(viewRange, LazyDataSeries { paddedJankyFrames }))
    }
  }
}