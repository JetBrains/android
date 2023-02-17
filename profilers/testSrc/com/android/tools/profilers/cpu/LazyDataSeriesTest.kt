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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.ArrayList
import java.util.concurrent.TimeUnit

class LazyDataSeriesTest {
  private val timer = FakeTimer()
  private lateinit var capture: CpuCapture

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", FakeTransportService(timer))

  @Before
  fun setup() {
    val parser = AtraceParser(MainProcessSelector(idHint = 1))
    capture = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"), 2)
  }

  @Test
  fun testCaptureDataRange() {
    val testSeriesData = buildSeriesData(1, 100, 10)
    val series = LazyDataSeries<ThreadState> { testSeriesData }
    // Test get exact data.
    var seriesData: List<SeriesData<ThreadState>> =
      series.getDataForRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(100).toDouble()
        )
      )
    verifySeriesDataMatches(seriesData, testSeriesData, 0, 10)

    // Test no overlap returns one result. This result should be the last.
    seriesData =
      series.getDataForRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        )
      )
    verifySeriesDataMatches(seriesData, testSeriesData, 9, 10)

    // Test trace info starts before series data [xxxx|xx]----| returns only valid overlapped range.
    seriesData =
      series.getDataForRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(0).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(50).toDouble()
        )
      )
    verifySeriesDataMatches(seriesData, testSeriesData, 0, 5)

    // Test trace info overlaps end of series data |-----[xx|xxx] returns only data starting at just before 50 up to max data.
    seriesData =
      series.getDataForRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        )
      )
    verifySeriesDataMatches(seriesData, testSeriesData, 5, 10)

    // Test trace info is subset of series data [xxx|xxxx|xx] returns only data within range
    val minUs = TimeUnit.MILLISECONDS.toMicros(50)
    val maxUs = TimeUnit.MILLISECONDS.toMicros(75)
    seriesData =
      series.getDataForRange(
        Range(
          minUs.toDouble(),
          maxUs.toDouble()
        )
      )
    verifySeriesDataMatches(seriesData, testSeriesData, 5, 8)

    // Test last element is returned if we request last bit of data.
    seriesData =
      series.getDataForRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(99).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(100).toDouble()
        )
      )
    verifySeriesDataMatches(seriesData, testSeriesData, 9, 10)
  }

  @Test
  fun testEmptySeries() {
    val testSeriesData = buildSeriesData(1, 100, 0)
    val series = LazyDataSeries<ThreadState> { testSeriesData }
    // Test getting data for an empty series doesn't cause issues and returns nothing.
    val seriesData: List<SeriesData<ThreadState>> =
      series.getDataForRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(100).toDouble()
        )
      )
    assertThat(seriesData).hasSize(0)
  }

  private fun verifySeriesDataMatches(
    seriesData: List<SeriesData<ThreadState>>,
    testSeriesData: List<SeriesData<ThreadState>>,
    startIndex: Int,
    endIndex: Int
  ) {
    var j = 0
    for (i in startIndex until endIndex) {
      assertThat(seriesData[j]).isEqualTo(testSeriesData[i])
      j++
    }
  }

  private fun buildSeriesData(startTime: Long, endTime: Long, count: Int): List<SeriesData<ThreadState>> {
    val seriesData = ArrayList<SeriesData<ThreadState>>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      seriesData.add(SeriesData(TimeUnit.MILLISECONDS.toMicros(time), ThreadState.RUNNING))
    }
    return seriesData
  }
}
