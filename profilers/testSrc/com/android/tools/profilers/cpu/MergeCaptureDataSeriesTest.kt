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
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.cpu.systemtrace.AtraceParser
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

// TODO reinvestigate these tests because the fake service currently returns all added threads regardless of timestamps which is wrong.
// Hence the data series returned from getDataForRange are overcounting and do not accurately reflect the request time range.
class MergeCaptureDataSeriesTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  var grpcChannel = FakeGrpcChannel("MergeCaptureDataSeriesTestChannel", transportService)
  private val profilerClient by lazy { ProfilerClient(grpcChannel.channel) }

  private lateinit var mergeCaptureDataSeries: MergeCaptureDataSeries<ThreadState>
  private lateinit var capture: CpuCapture

  @Before
  @Throws(Exception::class)
  fun setUp() {
    val myParser = AtraceParser(MainProcessSelector(idHint = 1))
    capture = myParser.parse(CpuProfilerTestUtils.getTraceFile("atrace_processid_1.ctrace"), 0)
    capture.range.set(TimeUnit.MILLISECONDS.toMicros(50).toDouble(), TimeUnit.MILLISECONDS.toMicros(150).toDouble())
    val aTraceSeries = LazyDataSeries { buildSeriesData(50, 150, 10) }
    val threadId = 1
    val threadStateSeries = CpuThreadStateDataSeries(profilerClient.transportClient, ProfilersTestData.SESSION_DATA.streamId,
                                                     ProfilersTestData.SESSION_DATA.pid, threadId, capture)
    mergeCaptureDataSeries = MergeCaptureDataSeries(capture, threadStateSeries, aTraceSeries)
    buildThreadActivityData(threadId)
  }

  @Test
  fun testGetDataNoTrace() {
    val stateSeries = mergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(201).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(400).toDouble()
      )
    )
    // 1 because only the last one from ThreadStateDataSeries.
    assertThat(stateSeries).hasSize(1)
  }

  @Test
  fun testGetDataTrace() {
    val stateSeries = mergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(1).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(400).toDouble()
      )
    )
    // 23 because 13 from ThreadStateDataSeries + 10 from MergeStateDataSeries.
    assertThat(stateSeries).hasSize(23)
  }

  @Test
  fun testGetDataTraceStartOverlap() {
    val stateSeries = mergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(50).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(200).toDouble()
      )
    )
    // 19 because 9 from ThreadStateDataSeries + 10 from MergeStateDataSeries.
    assertThat(stateSeries).hasSize(19)
  }

  @Test
  fun testGetDataTraceEndOverlap() {
    val stateSeries = mergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(0).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(150).toDouble()
      )
    )
    // 16 because 6 from ThreadStateDataSeries + 10 from MergeStateDataSeries.
    assertThat(stateSeries).hasSize(16)
  }

  @Test
  fun testGetDataTraceDataOnly() {
    val stateSeries = mergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(150).toDouble()
      )
    )
    // Only get some of the trace data.
    // 6 because 1 from ThreadStateDataSeries + 5 from MergeStateDataSeries
    assertThat(stateSeries).hasSize(6)
  }

  @Test
  fun testGetDataNoTraceGetsSampledData() {
    val aTraceSeries = LazyDataSeries { buildSeriesData(50, 150, 0) }
    val threadStateSeries = CpuThreadStateDataSeries(profilerClient.transportClient, ProfilersTestData.SESSION_DATA.streamId,
                                                     ProfilersTestData.SESSION_DATA.pid, 1, capture)
    mergeCaptureDataSeries = MergeCaptureDataSeries(capture, threadStateSeries, aTraceSeries)
    val stateSeries = mergeCaptureDataSeries.getDataForRange(
      Range(
        TimeUnit.MILLISECONDS.toMicros(100).toDouble(),
        TimeUnit.MILLISECONDS.toMicros(150).toDouble()
      )
    )
    assertThat(stateSeries).hasSize(8)
  }

  private fun buildSeriesData(startTime: Long, endTime: Long, count: Int): List<SeriesData<ThreadState>> {
    val seriesData = ArrayList<SeriesData<ThreadState>>()
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      seriesData.add(SeriesData(TimeUnit.MILLISECONDS.toMicros(time), ThreadState.SLEEPING))
    }
    return seriesData
  }

  private fun buildThreadActivityData(threadId: Int) {
    val startTime = 1L
    val endTime = 201L
    val count = 20
    for (i in 0 until count) {
      val time = startTime + (((endTime - startTime) / count) * i)
      transportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setTimestamp(TimeUnit.MILLISECONDS.toNanos(time))
          .setKind(Common.Event.Kind.CPU_THREAD)
          .setGroupId(threadId.toLong())
          .setIsEnded(false)
          .setCpuThread(Cpu.CpuThreadData.newBuilder().setTid(threadId).setName("Thread").setState(Cpu.CpuThreadData.State.RUNNING))
          .build()
      )
    }
  }
}
