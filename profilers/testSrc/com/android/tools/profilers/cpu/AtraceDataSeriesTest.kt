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
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

class AtraceDataSeriesTest {
  private lateinit var myStage: CpuProfilerStage

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", FakeCpuService(), FakeProfilerService())

  @Before
  fun setup() {
    val profilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), FakeTimer())
    myStage = CpuProfilerStage(profilers)
    val parser = AtraceParser(1)
    val capture = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"))
    myStage.capture = capture;
  }

  @Test
  fun testCaptureDataRange() {
    val testSeriesData = buildSeriesData(1, 100, 10)
    val series = AtraceDataSeries<CpuProfilerStage.ThreadState>(myStage, { _ -> testSeriesData })
    // Test get exact data.
    var seriesData: List<SeriesData<CpuProfilerStage.ThreadState>> =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(100).toDouble()
            )
        )
    verifySeriesDataMatches(seriesData, testSeriesData, 0, 10)

    // Test no overlap returns one result. This result should be the last.
    seriesData =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(150).toDouble()
            )
        )
    verifySeriesDataMatches(seriesData, testSeriesData, 9, 10)

    // Test trace info starts before series data [xxxx|xx]----| returns only valid overlapped range.
    seriesData =
        series.getDataForXRange(
            Range(
                TimeUnit.MILLISECONDS.toMicros(0).toDouble(),
                TimeUnit.MILLISECONDS.toMicros(50).toDouble()
            )
        )
    verifySeriesDataMatches(seriesData, testSeriesData, 0, 5)

    // Test trace info overlaps end of series data |-----[xx|xxx] returns only data starting at just before 50 up to max data.
    seriesData =
        series.getDataForXRange(
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
        series.getDataForXRange(
            Range(
                minUs.toDouble(),
                maxUs.toDouble()
            )
        )
    verifySeriesDataMatches(seriesData, testSeriesData, 5, 8)

    // Test last element is returned if we request last bit of data.
    seriesData =
        series.getDataForXRange(
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
    val series = AtraceDataSeries<CpuProfilerStage.ThreadState>(myStage, { _ -> testSeriesData })
    // Test getting data for an empty series doesn't cause issues and returns nothing.
    var seriesData: List<SeriesData<CpuProfilerStage.ThreadState>> =
      series.getDataForXRange(
        Range(
          TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
          TimeUnit.MILLISECONDS.toMicros(100).toDouble()
        )
      )
    assertThat(seriesData).hasSize(0)
  }

  private fun verifySeriesDataMatches(
    seriesData: List<SeriesData<CpuProfilerStage.ThreadState>>,
    testSeriesData: List<SeriesData<CpuProfilerStage.ThreadState>>,
    startIndex: Int,
    endIndex: Int
  ) {
    var j = 0
    for (i in startIndex until endIndex) {
      assertThat(seriesData[j]).isEqualTo(testSeriesData[i])
      j++
    }
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
