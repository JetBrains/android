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

import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.profilers.cpu.LazyDataSeries
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable
import perfetto.protos.PerfettoTrace.FrameTimelineEvent.JankType
import perfetto.protos.PerfettoTrace.FrameTimelineEvent.PresentType
import kotlin.math.max

class AndroidFrameTimelineModel constructor(events: List<AndroidFrameTimelineEvent>,
                                vsyncs: List<SeriesData<Long>>,
                                val viewRange: Range,
                                val multiSelectionModel: MultiSelectionModel<CpuAnalyzable<*>>,
                                val capture: SystemTraceCpuCapture) : StateChartModel<AndroidFrameTimelineEvent>() {
  val vsyncSeries = RangedSeries(viewRange, LazyDataSeries { vsyncs })

  var activeSeriesIndex = -1
    set(index) {
      if (index != field) {
        field = index
        changed(Aspect.MODEL_CHANGED)
      }
    }

  init {
    val layers = events.groupBy { it.layoutDepth }.toSortedMap(compareByDescending { it }).values
    layers.forEach { frames ->
      val paddedFrames = frames.padded({ it.expectedStartUs }, { max(it.actualEndUs, it.expectedEndUs) }, { it }, { _, _ -> null })
      addSeries(RangedSeries(viewRange, LazyDataSeries { paddedFrames }))
    }
  }
}

fun JankType.getTitle() = when (this) {
  JankType.JANK_APP_DEADLINE_MISSED -> "Deadline missed"
  JankType.JANK_BUFFER_STUFFING -> "Buffer stuffing"
  JankType.JANK_UNKNOWN -> "Unknown"
  JankType.JANK_NONE -> "No jank"
  else -> "Unspecified"
}

fun PresentType.getTitle() = when (this) {
  PresentType.PRESENT_DROPPED -> "Dropped"
  PresentType.PRESENT_EARLY -> "Early"
  PresentType.PRESENT_LATE -> "Late"
  PresentType.PRESENT_ON_TIME -> "On time"
  PresentType.PRESENT_UNKNOWN -> "Unknown"
  PresentType.PRESENT_UNSPECIFIED -> "Unspecified"
}