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

  private val myProfilerService = FakeProfilerService()

  private val myCpuService = FakeCpuService()

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, myProfilerService,
      FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build())

  private var myMergeCaptureDataSeries: MergeCaptureDataSeries? = null

  @Before
  @Throws(Exception::class)
  fun setUp() {
    val timer = FakeTimer()
    val services = FakeIdeProfilerServices()
    val profilers = StudioProfilers(myGrpcChannel.client, services, timer)
    val stage = CpuProfilerStage(profilers)
    stage.studioProfilers.stage = stage
    val aTraceSeries = AtraceThreadStateDataSeries()
    aTraceSeries.addCaptureSeriesData(
        Range(
            TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        ), buildSeriesData(50, 150, 10)
    )
    val threadStateSeries = ThreadStateDataSeries(stage, ProfilersTestData.SESSION_DATA, 1)
    myMergeCaptureDataSeries = MergeCaptureDataSeries(threadStateSeries, aTraceSeries)
    myCpuService.addAdditionalThreads(1, "Thread", buildThreadActivityData(1, 200, 20))
  }

  @Test
  fun testGetDataNoTrace() {
    val stateSeries = myMergeCaptureDataSeries!!.getDataForXRange(
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
    val stateSeries = myMergeCaptureDataSeries!!.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(400).toDouble()
        )
    )
    // 54 because 22 from ThreadStateDataSeries + 10 from MergeStateDataSeries + 22 from ThreadStateDataSeries
    // |---[xxxx]---|, FakeCpuService does not filter on time, as such we get the ThreadStateDataSeries twice.
    assertThat(stateSeries).hasSize(54)
  }

  @Test
  fun testGetDataTraceStartOverlap() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries!!.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(200).toDouble()
        )
    )
    // 32 because 22 from ThreadStateDataSeries + 10 from MergeStateDataSeries |[xxxx]---|
    assertThat(stateSeries).hasSize(32)
  }

  @Test
  fun testGetDataTraceEndOverlap() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries!!.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        )
    )
    // 32 because 22 from ThreadStateDataSeries + 10 from MergeStateDataSeries |---[xxxx]|
    assertThat(stateSeries).hasSize(32)
  }

  @Test
  fun testGetDataTraceDataOnly() {
    myCpuService.profilerType = CpuProfiler.CpuProfilerType.ATRACE
    myCpuService.addTraceInfo(buildTraceInfo(50, 150))
    val stateSeries = myMergeCaptureDataSeries!!.getDataForXRange(
        Range(
            TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
            TimeUnit.MILLISECONDS.toMicros(150).toDouble()
        )
    )
    // Only get some of the trace data. [xxx|xxxx|xxx]
    assertThat(stateSeries).hasSize(5)
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
