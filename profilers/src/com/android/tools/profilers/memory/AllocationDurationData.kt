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

import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.Companion.getModeFromFrequency
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.NONE
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
import com.android.tools.profilers.memory.adapters.CaptureObject

/**
 * This class implements duration data for a finished live allocation recording
 */
class AllocationDurationData<T: CaptureObject>(duration: Long, captureEntry: CaptureEntry<T>, val start: Double, val end: Double)
  : CaptureDurationData<T>(duration, true, false, captureEntry) {

  companion object {

    @JvmStatic
    fun makeModel(viewRange: Range, dataRange: Range,
                  allocSeries: DataSeries<CaptureDurationData<out CaptureObject>>,
                  samplingSeries: DataSeries<AllocationSamplingRateDurationData>) =
      DurationDataModel(RangedSeries(viewRange, makeDurationData(dataRange, allocSeries, samplingSeries)))

    private fun makeDurationData(dataRange: Range,
                                 allocSeries: DataSeries<CaptureDurationData<out CaptureObject>>,
                                 samplingSeries: DataSeries<AllocationSamplingRateDurationData>) =
      DataSeries { _ ->
        samplingSeries.getDataForRange(dataRange).consecutiveAllocRanges().mapNotNull {
          val startTime = it.min.toLong()
          val durationUs = it.max.toLong() - startTime
          val rawData = allocSeries.getDataForRange(it)
          if (rawData.isEmpty()) {
            null
          } else {
            val data = AllocationDurationData(durationUs, rawData[0].value.captureEntry, it.min, it.max)
            SeriesData(startTime, data as CaptureDurationData<out CaptureObject>)
          }
        }
      }

    /**
     * Each group of consecutive sampling rates of `FULL` or `SAMPLED` makes an allocation session
     */
    private fun List<SeriesData<AllocationSamplingRateDurationData>>.consecutiveAllocRanges() = mutableListOf<Range>().also { ranges ->
      var lo = Double.NaN
      forEach {
        when (getModeFromFrequency(it.value.currentRate.samplingNumInterval)) {
          NONE -> if (!lo.isNaN()) {
            ranges.add(Range(lo, it.x.toDouble()))
            lo = Double.NaN
          }
          SAMPLED, FULL -> if (lo.isNaN()) {
            lo = it.x.toDouble()
          }
        }
      }
    }
  }
}