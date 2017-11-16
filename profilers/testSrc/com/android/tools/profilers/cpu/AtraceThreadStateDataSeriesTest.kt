// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.proto.CpuProfiler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

class AtraceThreadStateDataSeriesTest {

  @Test
  fun testCaptureDataAvailable() {
    val series = AtraceThreadStateDataSeries()
    val validRange = Range(1.0,100.0)
    assertThat(series.getOverlapRange(Range(0.0, 1.0))).isNull()
    series.addCaptureSeriesData(validRange, buildSeriesData(1, 100, 10))

    // Test no overlap returns false
    assertThat(series.getOverlapRange(Range(100.0, 101.0))).isNull()
    // Test exact match. |[xxxxxxxxx]| returns true
    assertThat(series.getOverlapRange(Range(1.0, 100.0))).isEqualTo(validRange)
    // Test trace info starts before series data [xxxx|xx]----| returns true
    assertThat(series.getOverlapRange(Range(0.0, 50.0))).isEqualTo(validRange)
    // Test trace info overlaps end of series data |-----[xx|xxx] returns true
    assertThat(series.getOverlapRange(Range(50.0, 200.0))).isEqualTo(validRange)
    // Test trace info is subset of series data [xxx|xxxx|xx] returns true
    assertThat(series.getOverlapRange(Range(25.0, 75.0))).isEqualTo(validRange)
  }

  @Test
  fun testCaptureDataRange() {
    val series = AtraceThreadStateDataSeries()
    val validRange = Range(TimeUnit.MILLISECONDS.toMicros(1).toDouble(),TimeUnit.MILLISECONDS.toMicros(100).toDouble())
    series.addCaptureSeriesData(validRange, buildSeriesData(1, 100, 10))
    // Test get exact data.
    var seriesData: List<SeriesData<CpuProfilerStage.ThreadState>> =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(100).toDouble()
            )
        )
    verifySeriesData(seriesData, 1, 100)

    // Test no overlap returns false
    seriesData =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(150).toDouble()
            )
        )
    assertThat(seriesData).hasSize(0)

    // Test trace info starts before series data [xxxx|xx]----| returns only valid overlapped range.
    seriesData =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(0).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(50).toDouble()
            )
        )
    verifySeriesData(seriesData, 0, 50)

    // Test trace info overlaps end of series data |-----[xx|xxx] returns only data starting at 50 up to max data.
    seriesData =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(150).toDouble()
            )
        )
    verifySeriesData(seriesData, 50, 100)

    // Test trace info is subset of series data [xxx|xxxx|xx] returns only data within range
    seriesData =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(75).toDouble()
            )
        )
    verifySeriesData(seriesData, 50, 75)
  }

  private fun verifySeriesData(seriesData: List<SeriesData<CpuProfilerStage.ThreadState>>, min: Long, max: Long) {
    val minUs = TimeUnit.MILLISECONDS.toMicros(min)
    val maxUs = TimeUnit.MILLISECONDS.toMicros(max)
    assertThat(seriesData).isNotEmpty()
    assertThat(seriesData[0].x).isAtLeast(minUs)
    assertThat(seriesData[0].x).isLessThan(maxUs)
    assertThat(seriesData[seriesData.size - 1].x).isAtMost(maxUs)
    assertThat(seriesData[seriesData.size - 1].x).isGreaterThan(minUs)
  }

  private fun buildSeriesData(startTime: Long, endTime: Long, count: Int): List<SeriesData<CpuProfilerStage.ThreadState>> {
    val seriesData = ArrayList<SeriesData<CpuProfilerStage.ThreadState>>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      seriesData.add(SeriesData(TimeUnit.MILLISECONDS.toMicros(time), CpuProfilerStage.ThreadState.RUNNING))
    }
    return seriesData
  }
}
