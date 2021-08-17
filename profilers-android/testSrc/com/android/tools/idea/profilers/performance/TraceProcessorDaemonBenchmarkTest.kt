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
package com.android.tools.idea.profilers.performance

import com.android.tools.idea.profilers.perfetto.traceprocessor.TraceProcessorDaemonClient
import com.android.tools.idea.profilers.perfetto.traceprocessor.TraceProcessorDaemonQueryResult
import com.android.tools.idea.profilers.perfetto.traceprocessor.TraceProcessorServiceImpl
import com.android.tools.perflogger.Benchmark
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.google.common.base.Ticker
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraceProcessorDaemonBenchmarkTest {
  private val tpdQueryTimeBenchmark = Benchmark.Builder("TraceProcessorDaemon Query Time (millis)")
    .setProject("Android Studio Profilers")
    .build()
  private val tpdQueryResponseSizeBenchmark = Benchmark.Builder("TraceProcessorDaemon Query Response Size (kb)")
    .setProject("Android Studio Profilers")
    .build()

  private val tpdClient: TraceProcessorDaemonClient = TraceProcessorDaemonClient(Ticker.systemTicker())
  private val fakeTracker = FakeFeatureTracker()

  private enum class TestCase(val traceFile: File, val tradeId: Long, val processes: List<Int>) {
    TRACE_10S(CpuProfilerTestUtils.getTraceFile("performance/perfetto_10s_tanks.trace"), 10L,
              listOf(7366 /* com.google.android.tanks */, 606 /* surfaceflinger */)),
    TRACE_60S(CpuProfilerTestUtils.getTraceFile("performance/perfetto_60s_tanks.trace"), 60L,
              listOf(9796 /* com.google.android.tanks */, 645 /* surfaceflinger */)),
    TRACE_120S(CpuProfilerTestUtils.getTraceFile("performance/perfetto_120s_tanks.trace"), 120L,
               listOf(10679 /* com.google.android.tanks */, 626 /* surfaceflinger */)),
  }

  @After
  fun tearDown() {
    Disposer.dispose(tpdClient)
  }

  @Test
  fun benchmarkTpd_10s() {
    runBenchmark(TestCase.TRACE_10S)
  }

  @Test
  fun benchmarkTpd_60s() {
    runBenchmark(TestCase.TRACE_60S)
  }

  @Test
  fun benchmarkTpd_120s() {
    runBenchmark(TestCase.TRACE_120S)
  }

  private fun runBenchmark(case: TestCase) {
    // We start by making sure the daemon is up with an empty load request:
    val warmupLoadResponse = tpdClient.loadTrace(TraceProcessor.LoadTraceRequest.getDefaultInstance(), fakeTracker)
    assertTrue(warmupLoadResponse.completed)
    assertFalse(warmupLoadResponse.response!!.ok) // We should not have loaded anything here.

    // Now we measure the time to load a trace:
    val loadTraceProto = genLoadTraceRequestProto(case.traceFile, case.tradeId)
    lateinit var loadResponse: TraceProcessorDaemonQueryResult<TraceProcessor.LoadTraceResponse>
    tpdQueryTimeBenchmark.log("${case.name}-TraceLoad", measureTimeMillis {
      loadResponse = tpdClient.loadTrace(loadTraceProto, fakeTracker)
    })
    assertTrue(loadResponse.completed)
    assertTrue(loadResponse.response!!.ok)

    val cpuDataRequestProto = TraceProcessorServiceImpl.buildCpuDataRequestProto(case.tradeId, case.processes)
    lateinit var queryBatchResponse: TraceProcessorDaemonQueryResult<TraceProcessor.QueryBatchResponse>
    tpdQueryTimeBenchmark.log("${case.name}-CpuData", measureTimeMillis {
      queryBatchResponse = tpdClient.queryBatchRequest(cpuDataRequestProto, fakeTracker)
    })
    assertTrue(queryBatchResponse.completed)
    assertTrue(queryBatchResponse.response!!.resultList.all { it.ok })

    val responseSizeKb = queryBatchResponse.response!!.serializedSize / 1024L
    tpdQueryResponseSizeBenchmark.log("${case.name}-CpuData", responseSizeKb)
  }

  private fun genLoadTraceRequestProto(trace: File, id: Long) = TraceProcessor.LoadTraceRequest.newBuilder()
      .setTraceId(id)
      .setTracePath(trace.absolutePath)
      .build()
}