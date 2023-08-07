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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.Interpolatable
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.TimeAxisFormatter
import com.android.tools.adtui.model.legend.EventLegend
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.android.tools.adtui.model.legend.SeriesLegend
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.Companion.MEMORY_AXIS_FORMATTER
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.Companion.OBJECT_COUNT_AXIS_FORMATTER
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.Companion.getModeFromFrequency
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

class MemoryStageLegends private constructor(range: Range,
                                             isTooltip: Boolean,
                                             usage: DetailedMemoryUsage,
                                             isLiveAllocationTrackingReady: () -> Boolean) : LegendComponentModel(range) {
  constructor(usage: DetailedMemoryUsage, range: Range, isTooltip: Boolean, isLiveAllocationTrackingReady: () -> Boolean) :
    this(range, isTooltip, usage, isLiveAllocationTrackingReady)

  val javaLegend = SeriesLegend(usage.javaSeries, MEMORY_AXIS_FORMATTER, range)
  val nativeLegend = SeriesLegend(usage.nativeSeries, MEMORY_AXIS_FORMATTER, range)
  val graphicsLegend = SeriesLegend(usage.graphicsSeries, MEMORY_AXIS_FORMATTER, range)
  val stackLegend = SeriesLegend(usage.stackSeries, MEMORY_AXIS_FORMATTER, range)
  val codeLegend = SeriesLegend(usage.codeSeries, MEMORY_AXIS_FORMATTER, range)
  val otherLegend = SeriesLegend(usage.otherSeries, MEMORY_AXIS_FORMATTER, range)
  val totalLegend = SeriesLegend(usage.totalMemorySeries, MEMORY_AXIS_FORMATTER, range)
  val objectsLegend = SeriesLegend(usage.objectsSeries, OBJECT_COUNT_AXIS_FORMATTER, range, usage.objectsSeries.name,
                                   Interpolatable.RoundedSegmentInterpolator, Predicate { r ->
    // If live allocation is not enabled, show the object series as long as there is data.
    !isLiveAllocationTrackingReady() ||
    // Controls whether the series should be shown by looking at whether there is a FULL tracking mode event within the query range.
    usage.allocationSamplingRateDurations.series.getSeriesForRange(r).let { data ->
      data.isNotEmpty() && getModeFromFrequency(data.last().value.currentRate.samplingNumInterval) == FULL
    }
  })
  val gcDurationLegend = EventLegend("GC Duration") { duration: GcDurationData ->
    TimeAxisFormatter.DEFAULT.getFormattedString(TimeUnit.MILLISECONDS.toMicros(1).toDouble(), duration.durationUs.toDouble(), true)
  }
  val samplingRateDurationLegend = EventLegend("Tracking") { duration: AllocationSamplingRateDurationData ->
    getModeFromFrequency(duration.currentRate.samplingNumInterval).displayName
  }

  init {
    val legends =
      if (isTooltip) listOf(otherLegend, codeLegend, stackLegend, graphicsLegend, nativeLegend, javaLegend,
                            objectsLegend, samplingRateDurationLegend, gcDurationLegend, totalLegend)
      else listOf(totalLegend, javaLegend, nativeLegend, graphicsLegend, stackLegend, codeLegend, otherLegend, objectsLegend)
    legends.forEach(::add)
  }
}