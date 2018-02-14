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
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.atrace.AtraceParser
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

class MergeCaptureDataSeriesTest {
  private val myCpuService = FakeCpuService()

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService)

  private lateinit var myMergeCaptureDataSeries: MergeCaptureDataSeries<CpuProfilerStage.ThreadState>
  private lateinit var myStage: CpuProfilerStage;

  @Before
  @Throws(Exception::class)
  fun setUp() {
    val timer = FakeTimer()
    val services = FakeIdeProfilerServices()
    val profilers = StudioProfilers(myGrpcChannel.client, services, timer)
    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    val myParser = AtraceParser(1)
    val capture = myParser.parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"))
    capture.range.set(TimeUnit.MILLISECONDS.toMicros(50).toDouble(), TimeUnit.MILLISECONDS.toMicros(150).toDouble())
    myStage.capture = capture;
    val aTraceSeries = AtraceDataSeries<CpuProfilerStage.ThreadState>(myStage, {buildSeriesData(50, 150, 10)})
    val threadStateSeries = ThreadStateDataSeries(myStage, ProfilersTestData.SESSION_DATA, 1)
    myMergeCaptureDataSeries = MergeCaptureDataSeries<CpuProfilerStage.ThreadState>(myStage, threadStateSeries, aTraceSeries)
    myCpuService.addAdditionalThreads(1, "Thread", buildThreadActivityData(1, 200, 20))
  }

  @Test
  fun testGetDataNoTrace() {
    val stateSeries = myMergeCaptureDataSeries.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(201).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(400).toDouble()
        )
    )
    assertThat(stateSeries).hasSize(20)
  }

  @Test
  fun testGetDataTrace() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(400).toDouble()
        )
    )
    // 53 because 21 from ThreadStateDataSeries + 10 from MergeStateDataSeries + 22 from ThreadStateDataSeries
    // The last element of the first data series call is truncated because it exceeds the start of our trace info.
    // |---[xxxx]---|, FakeCpuService does not filter on time, as such we get the ThreadStateDataSeries twice.
    assertThat(stateSeries).hasSize(53)
  }

  @Test
  fun testGetDataTraceStartOverlap() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(200).toDouble()
        )
    )
    // 53 because 21 from ThreadStateDataSeries + 10 from MergeStateDataSeries + 22 from ThreadStateDataSeriesp
    // The last element of the first data series call is truncated because it exceeds the start of our trace info.
    // |---[xxxx]---|, FakeCpuService does not filter on time, as such we get the ThreadStateDataSeries twice.
    assertThat(stateSeries).hasSize(53)
  }

  @Test
  fun testGetDataTraceEndOverlap() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        )
    )
    // 31 because 22 from ThreadStateDataSeries + 10 from MergeStateDataSeries |---[xxxx]|
    // The last element of the first data series call is truncated because it exceeds the start of our trace info.
    assertThat(stateSeries).hasSize(31)
  }

  @Test
  fun testGetDataTraceDataOnly() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        )
    )
    // Only get some of the trace data. [xxx|xxxx|xxx]
    // 26 because 21 from ThreadStateDataSeries + 5 from MergeStateDataSeries
    // |[xxxx]|, FakeCpuService does not filter on time, as such we get the ThreadStateDataSeries when we go to pull the last element.
    assertThat(stateSeries).hasSize(26)
  }

  @Test
  fun testGetDataNoTraceGetsSampledData() {
    val aTraceSeries = AtraceDataSeries<CpuProfilerStage.ThreadState>(myStage, {buildSeriesData(50, 150, 0)})
    val threadStateSeries = ThreadStateDataSeries(myStage, ProfilersTestData.SESSION_DATA, 1)
    myMergeCaptureDataSeries = MergeCaptureDataSeries<CpuProfilerStage.ThreadState>(myStage, threadStateSeries, aTraceSeries)
    val stateSeries = myMergeCaptureDataSeries.getDataForXRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(150).toDouble()
      )
    )
    assertThat(stateSeries).hasSize(20)
  }

  private fun buildTraceInfo(fromTime: Long, toTime: Long): com.android.tools.profiler.proto.CpuProfiler.TraceInfo {
    return CpuProfiler.TraceInfo.newBuilder()
        .setFromTimestamp(TimeUnit.MILLISECONDS.toNanos(fromTime))
        .setToTimestamp(TimeUnit.MILLISECONDS.toNanos(toTime))
        .addThreads(CpuProfiler.Thread.newBuilder().setTid(1))
        .setProfilerType(CpuProfiler.CpuProfilerType.ATRACE)
        .build()
  }

  private fun buildSeriesData(startTime: Long, endTime: Long, count: Int): List<SeriesData<CpuProfilerStage.ThreadState>> {
    val seriesData = ArrayList<SeriesData<CpuProfilerStage.ThreadState>>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      seriesData.add(SeriesData(TimeUnit.MILLISECONDS.toMicros(time), CpuProfilerStage.ThreadState.SLEEPING))
    }
    return seriesData
  }

  private fun buildThreadActivityData(startTime: Long, endTime: Long, count: Int): List<CpuProfiler.GetThreadsResponse.ThreadActivity> {
    val activities = ArrayList<CpuProfiler.GetThreadsResponse.ThreadActivity>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      val activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
          .setNewState(CpuProfiler.GetThreadsResponse.State.RUNNING)
          .setTimestamp(TimeUnit.MILLISECONDS.toMicros(time))
          .build()
      activities.add(activity)
    }
    return activities
  }
}
