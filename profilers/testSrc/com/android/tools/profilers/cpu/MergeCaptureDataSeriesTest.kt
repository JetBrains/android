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
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.CpuServiceGrpc
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.ArrayList
import java.util.concurrent.TimeUnit

// TODO reinvestigate these tests because the fake service currently returns all added threads regardless of timestamps which is wrong.
// Hence the data series returned from getDataForRange are overcounting and do not accurately reflect the request time range.
class MergeCaptureDataSeriesTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer)
  private val myCpuService = FakeCpuService()

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel("CpuProfilerStageTestChannel", myTransportService, myCpuService)
  private val myProfilerClient by lazy { ProfilerClient(myGrpcChannel.channel) }

  private lateinit var myMergeCaptureDataSeries: MergeCaptureDataSeries<ThreadState>
  private lateinit var myStage: CpuProfilerStage

  @Before
  @Throws(Exception::class)
  fun setUp() {
    myCpuService.clearTraceInfo()
    val services = FakeIdeProfilerServices()
    val profilers = StudioProfilers(myProfilerClient, services, myTimer)
    myStage = CpuProfilerStage(profilers)
    myStage.studioProfilers.stage = myStage
    val myParser = AtraceParser(MainProcessSelector(idHint = 1))
    val capture = myParser.parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"), 0)
    capture.range.set(TimeUnit.MILLISECONDS.toMicros(50).toDouble(), TimeUnit.MILLISECONDS.toMicros(150).toDouble())
    myStage.capture = capture
    val aTraceSeries = LazyDataSeries<ThreadState> { buildSeriesData(50, 150, 10) }
    val threadStateSeries = LegacyCpuThreadStateDataSeries(myProfilerClient.cpuClient, ProfilersTestData.SESSION_DATA, 1, capture)
    myMergeCaptureDataSeries = MergeCaptureDataSeries<ThreadState>(capture, threadStateSeries, aTraceSeries)
    myCpuService.addThreads(1, "Thread", buildThreadActivityData(1, 200, 20))
  }

  @Test
  fun testGetDataNoTrace() {
    val stateSeries = myMergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(201).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(400).toDouble()
      )
    )
    assertThat(stateSeries).hasSize(22)
  }

  @Test
  fun testGetDataTrace() {
    val stateSeries = myMergeCaptureDataSeries.getDataForRange(
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
    val stateSeries = myMergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(200).toDouble()
      )
    )
    // 53 because 21 from ThreadStateDataSeries + 10 from MergeStateDataSeries + 22 from ThreadStateDataSeries
    // The last element of the first data series call is truncated because it exceeds the start of our trace info.
    // |---[xxxx]---|, FakeCpuService does not filter on time, as such we get the ThreadStateDataSeries twice.
    assertThat(stateSeries).hasSize(53)
  }

  @Test
  fun testGetDataTraceEndOverlap() {
    val stateSeries = myMergeCaptureDataSeries.getDataForRange(
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
    val stateSeries = myMergeCaptureDataSeries.getDataForRange(
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
    val capture = myStage.capture!!
    val aTraceSeries = LazyDataSeries<ThreadState> { buildSeriesData(50, 150, 0) }
    val threadStateSeries = LegacyCpuThreadStateDataSeries(myProfilerClient.cpuClient, ProfilersTestData.SESSION_DATA, 1, myStage.capture)
    myMergeCaptureDataSeries = MergeCaptureDataSeries<ThreadState>(capture, threadStateSeries, aTraceSeries)
    val stateSeries = myMergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(150).toDouble()
      )
    )
    assertThat(stateSeries).hasSize(22)
  }

  @Test
  fun testGetDataIsCalledWithRangeUptoFirstState() {
    // Our capture range is 50 -> 150, so we create a data sample that starts at 100 to ensure we get a ThreadStateDataSeries range call
    // from 0 -> 100
    val capture = myStage.capture!!
    val aTraceSeries = LazyDataSeries<ThreadState> { buildSeriesData(100, 150, 10) }
    val threadStateSeries = FakeLegacyCpuThreadStateDataSeries(myProfilerClient.cpuClient, ProfilersTestData.SESSION_DATA, 1,
                                                               myStage.capture)
    myMergeCaptureDataSeries = MergeCaptureDataSeries<ThreadState>(capture, threadStateSeries, aTraceSeries)
    myMergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(0).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(200).toDouble()
      )
    )
    assertThat(threadStateSeries.calledWithRanges).hasSize(2)
    assertThat(threadStateSeries.calledWithRanges[0].min).isWithin(EPSILON).of(TimeUnit.MILLISECONDS.toMicros(0).toDouble())
    assertThat(threadStateSeries.calledWithRanges[0].max).isWithin(EPSILON).of(TimeUnit.MILLISECONDS.toMicros(100).toDouble())
    assertThat(threadStateSeries.calledWithRanges[1].min).isWithin(EPSILON).of(TimeUnit.MILLISECONDS.toMicros(150).toDouble())
    assertThat(threadStateSeries.calledWithRanges[1].max).isWithin(EPSILON).of(TimeUnit.MILLISECONDS.toMicros(200).toDouble())
  }

  private fun buildSeriesData(startTime: Long, endTime: Long, count: Int): List<SeriesData<ThreadState>> {
    val seriesData = ArrayList<SeriesData<ThreadState>>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      seriesData.add(SeriesData(TimeUnit.MILLISECONDS.toMicros(time), ThreadState.SLEEPING))
    }
    return seriesData
  }

  private fun buildThreadActivityData(startTime: Long, endTime: Long, count: Int): List<CpuProfiler.GetThreadsResponse.ThreadActivity> {
    val activities = ArrayList<CpuProfiler.GetThreadsResponse.ThreadActivity>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      val activity = CpuProfiler.GetThreadsResponse.ThreadActivity.newBuilder()
        .setNewState(Cpu.CpuThreadData.State.RUNNING)
        .setTimestamp(TimeUnit.MILLISECONDS.toMicros(time))
        .build()
      activities.add(activity)
    }
    return activities
  }

  private class FakeLegacyCpuThreadStateDataSeries(stub: CpuServiceGrpc.CpuServiceBlockingStub,
                                                   session: Common.Session,
                                                   tid: Int,
                                                   capture: CpuCapture?) : LegacyCpuThreadStateDataSeries(stub, session, tid, capture) {
    val calledWithRanges = arrayListOf<Range>()

    override fun getDataForRange(range: Range?): MutableList<SeriesData<ThreadState>> {
      calledWithRanges.add(range!!)
      return super.getDataForRange(range)
    }
  }

  companion object {
    const val EPSILON = 1e-3
  }
}
