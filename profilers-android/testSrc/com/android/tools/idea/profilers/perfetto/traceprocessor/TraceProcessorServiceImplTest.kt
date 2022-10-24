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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.idea.profilers.performance.TraceProcessorDaemonBenchmarkTest.Companion.fakeProcess
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessorServiceGrpc
import com.android.tools.profilers.FakeFeatureTracker
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.utils.Pair
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats
import com.intellij.openapi.util.io.FileUtil
import com.android.tools.idea.io.grpc.stub.StreamObserver
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class TraceProcessorServiceImplTest {

  private val fakeGrpcService = TPServiceInMemoryForTesting()
  private val fakeTicker = FakeTicker(10, TimeUnit.MILLISECONDS)
  private val fakeIdeProfilerServices = FakeIdeProfilerServices()
  private val fakeFeatureTracker = fakeIdeProfilerServices.featureTracker as FakeFeatureTracker
  private val fakeProcess = ProcessModel(1, "", emptyMap(), emptyMap())

  @get:Rule
  val tempFolder = TemporaryFolder()

  @get:Rule
  val fakeGrpcChannel = FakeGrpcChannel("TraceProcessorServiceImplTest", fakeGrpcService)

  @Test
  fun `loadTrace - ok`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    traceFile.writeBytes(Random.Default.nextBytes(256))

    val traceLoaded = ideService.loadTrace(10, traceFile, fakeIdeProfilerServices)

    assertThat(traceLoaded).isTrue()
    val symbolsFile = File("${FileUtil.getTempDirectory()}${File.separator}10.symbols")
    val expectedRequest = TraceProcessor.LoadTraceRequest.newBuilder()
      .setTraceId(10)
      .setTracePath(traceFile.absolutePath)
      .addSymbolPath("/fake/sym/dir/")
      .setSymbolizedOutputPath(symbolsFile.absolutePath)
      .build()
    assertThat(fakeGrpcService.lastLoadTraceRequest).isEqualTo(expectedRequest)

    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsExactly(
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE, getOkMetricStatsFor(10, 10, 256)))
  }

  @Test
  fun `loadTrace - fail`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(false)
      .setError("Testing Failure")
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    traceFile.writeBytes(Random.Default.nextBytes(256))
    val traceLoaded = ideService.loadTrace(10, traceFile, fakeIdeProfilerServices)
    assertThat(traceLoaded).isFalse()
    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsExactly(
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE, getErrorMetricStatsFor(10, 10, 256)))
  }

  @Test
  fun `loadTrace - grpc retry`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    fakeGrpcService.failsPerQuery = 2
    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    traceFile.writeBytes(Random.Default.nextBytes(256))
    val traceLoaded = ideService.loadTrace(10, traceFile, fakeIdeProfilerServices)
    assertThat(traceLoaded).isTrue()
    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsExactly(
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE, getOkMetricStatsFor(10, 10, 256)))
  }

  @Test
  fun `loadTrace - grpc retry exhausted`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    fakeGrpcService.failsPerQuery = 5
    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    traceFile.writeBytes(Random.Default.nextBytes(256))

    try {
      ideService.loadTrace(10, traceFile, fakeIdeProfilerServices)
      fail()
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("TPD Service: Fail to load trace 10: Unable to reach TPDaemon.")
    }

    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsExactly(
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE, getFailMetricStatsFor(10, 10, 256)))
  }

  @Test
  fun `loadCpuData - ok`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    // For test simplicity here, will return a single result (the real case would be one for each query in the batch)
    fakeGrpcService.queryBatchResponse = TraceProcessor.QueryBatchResponse.newBuilder()
      .addResult(TraceProcessor.QueryResult.newBuilder().setOk(true))
      .build()

    ideService.loadCpuData(10, listOf(fakeProcess(33), fakeProcess(42)),
                           ProcessModel(123, "foo", emptyMap(), emptyMap()), fakeIdeProfilerServices)

    val expectedRequest = TraceProcessor.QueryBatchRequest.newBuilder()
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setProcessMetadataRequest(TraceProcessor.QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setSchedRequest(TraceProcessor.QueryParameters.SchedulingEventsParameters.getDefaultInstance()))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setCpuCoreCountersRequest(TraceProcessor.QueryParameters.CpuCoreCountersParameters.getDefaultInstance()))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setAndroidFrameEventsRequest(
                    TraceProcessor.QueryParameters.AndroidFrameEventsParameters.newBuilder().setLayerNameHint("foo")))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setPowerCounterTracksRequest(TraceProcessor.QueryParameters.PowerCounterTracksParameters.getDefaultInstance()))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setAndroidFrameTimelineRequest(
                    TraceProcessor.QueryParameters.AndroidFrameTimelineParameters.newBuilder().setProcessId(123)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setTraceEventsRequest(TraceProcessor.QueryParameters.TraceEventsParameters.newBuilder().setProcessId(33)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setProcessCountersRequest(TraceProcessor.QueryParameters.ProcessCountersParameters.newBuilder().setProcessId(33)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setTraceEventsRequest(TraceProcessor.QueryParameters.TraceEventsParameters.newBuilder().setProcessId(42)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setProcessCountersRequest(TraceProcessor.QueryParameters.ProcessCountersParameters.newBuilder().setProcessId(42)))
      .build()
    assertThat(fakeGrpcService.lastQueryBatchRequest).isEqualTo(expectedRequest)

    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsExactly(
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_CPU_DATA, getOkMetricStatsFor(30, 10)))
  }

  @Test
  fun `loadCpuData - reload trace if necessary`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    traceFile.writeBytes(Random.Default.nextBytes(256))

    ideService.loadTrace(10, traceFile, fakeIdeProfilerServices)
    fakeGrpcService.lastLoadTraceRequest = null // Mark as a null, so we can verify below it was called

    // For test simplicity here, will return a single result (the real case would be one for each query in the batch)
    fakeGrpcService.queryBatchResponse = TraceProcessor.QueryBatchResponse.newBuilder()
      .addResult(TraceProcessor.QueryResult.newBuilder()
                   .setOk(false)
                   .setFailureReason(TraceProcessor.QueryResult.QueryFailureReason.TRACE_NOT_FOUND))
      .build()

    ideService.loadCpuData(10, listOf(fakeProcess(33), fakeProcess(42)), fakeProcess, fakeIdeProfilerServices)
    // Can't do assertThat(...).isNotNull() because of a problem that assertThat(Any?).isNotNull()
    fakeGrpcService.lastLoadTraceRequest ?: fail("Expected lastLoadTraceRequest to not be null")

    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsAllOf(
      // First loadTrace
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE, getOkMetricStatsFor(10, 10, 256)),
      // Implicit loadTrace called by loadCpuData to try to recover the missing trace.
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE, getOkMetricStatsFor(10, 10, 256)))
  }

  @Test
  fun `loadCpuData - trace not loaded`() {
    val client = TraceProcessorDaemonClient(fakeTicker, TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(fakeTicker, client)

    // For test simplicity here, will return a single result (the real case would be one for each query in the batch)
    fakeGrpcService.queryBatchResponse = TraceProcessor.QueryBatchResponse.newBuilder()
      .addResult(TraceProcessor.QueryResult.newBuilder()
                   .setOk(false)
                   .setFailureReason(TraceProcessor.QueryResult.QueryFailureReason.TRACE_NOT_FOUND))
      .build()

    try {
      ideService.loadCpuData(10, listOf(fakeProcess(33), fakeProcess(42)), fakeProcess, fakeIdeProfilerServices)
      fail()
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("TPD Service: Fail to get cpu data for trace 10: Trace 10 needs to be loaded before querying.")
    }

    // We never issue a load trace since we don't know about the trace.
    assertThat(fakeGrpcService.lastLoadTraceRequest).isNull()

    assertThat(fakeFeatureTracker.traceProcessorQueryMetrics).containsExactly(
      Pair.of(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_CPU_DATA, getFailMetricStatsFor(30, 10)))
  }

  private class TPServiceInMemoryForTesting: TraceProcessorServiceGrpc.TraceProcessorServiceImplBase() {

    var loadTraceResponse = TraceProcessor.LoadTraceResponse.getDefaultInstance()
    var queryBatchResponse = TraceProcessor.QueryBatchResponse.getDefaultInstance()

    var lastLoadTraceRequest: TraceProcessor.LoadTraceRequest? = null
    var lastQueryBatchRequest: TraceProcessor.QueryBatchRequest? = null

    // How many times to fail a same request before answering successfully
    var failsPerQuery = 0

    private var failCountPerQuery = mutableMapOf<Any, Int>()

    @Override
    override fun loadTrace(request: TraceProcessor.LoadTraceRequest, responseObserver: StreamObserver<TraceProcessor.LoadTraceResponse>) {
      lastLoadTraceRequest = request
      checkFailuresAndReplyRequest(request, loadTraceResponse, responseObserver)
    }

    @Override
    override fun queryBatch(request: TraceProcessor.QueryBatchRequest,
                            responseObserver: StreamObserver<TraceProcessor.QueryBatchResponse>) {
      lastQueryBatchRequest = request
      checkFailuresAndReplyRequest(request, queryBatchResponse, responseObserver)
    }

    private fun <T> checkFailuresAndReplyRequest(request: Any, response: T, observer: StreamObserver<T>) {
      val currentFailures = failCountPerQuery.getOrDefault(request, 0)
      if (currentFailures >= failsPerQuery) {
        failCountPerQuery.remove(request)
        observer.onNext(response)
        observer.onCompleted()
      } else {
        failCountPerQuery[request] = currentFailures + 1
        observer.onError(RuntimeException("RPC refused for testing"))
      }
    }
  }

  private fun getOkMetricStatsFor(methodTimeMs: Long , queryTimeMs: Long, traceSizeBytes: Long? = null): TraceProcessorDaemonQueryStats {
    val builder =  TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(TraceProcessorDaemonQueryStats.QueryReturnStatus.OK)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)

    traceSizeBytes?.let { builder.setTraceSizeBytes(it) }

    return builder.build()
  }

  private fun getErrorMetricStatsFor(methodTimeMs: Long , queryTimeMs: Long, traceSizeBytes: Long? = null): TraceProcessorDaemonQueryStats {
    val builder =  TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)

    traceSizeBytes?.let { builder.setTraceSizeBytes(it) }

    return builder.build()
  }

  private fun getFailMetricStatsFor(methodTimeMs: Long , queryTimeMs: Long, traceSizeBytes: Long? = null): TraceProcessorDaemonQueryStats {
    val builder =  TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)

    traceSizeBytes?.let { builder.setTraceSizeBytes(it) }

    return builder.build()
  }
}