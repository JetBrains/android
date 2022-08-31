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
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.*
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AllocationDurationDataTest {

  @Test
  fun `consecutive allocation intervals are grouped into one session`() {
    val viewRange = Range(0.0, 10.0)
    val dataRange = Range(0.0, 10.0)

    val allocSeries = DataSeries.using {
      listOf(makeAllocData(1),
             makeAllocData(3))
    }

    val samplingSeries = DataSeries.using {
      listOf(makeSamplingData(1, 1, FULL),
             makeSamplingData(2, 1, NONE),
             makeSamplingData(3, 1, SAMPLED),
             makeSamplingData(4, 2, FULL),
             makeSamplingData(6, 1, NONE))
    }

    val model = AllocationDurationData.makeModel(viewRange, dataRange, allocSeries, samplingSeries)
    model.series.series.let { series ->
      assertThat(series).hasSize(2)
      fun check(i: Int, x: Int, start: Double, end: Double) = series[i].let {
        assertThat(it.x).isEqualTo(x)
        assertThat(it.value).isInstanceOf(AllocationDurationData::class.java)
        assertThat((it.value as AllocationDurationData).start).isEqualTo(start)
        assertThat((it.value as AllocationDurationData).end).isEqualTo(end)
      }
      check(0, 1, 1.0, 2.0)
      check(1, 3, 3.0, 6.0)
    }
  }

  private fun makeAllocData(x: Long): SeriesData<CaptureDurationData<out CaptureObject>> =
    SeriesData(x, CaptureDurationData<CaptureObject>(1, false, false, CaptureEntry(x) {throw NotImplementedError()}))

  private fun makeSamplingData(x: Long, dur: Long, mode: BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode) =
    Memory.MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(mode.value).build().let {
      SeriesData(x, AllocationSamplingRateDurationData(dur, null, it))
    }
}