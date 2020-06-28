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

import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessorServiceGrpc
import com.google.common.truth.Truth.assertThat
import io.grpc.stub.StreamObserver
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.RuntimeException

class TraceProcessorServiceImplTest {

  private val fakeGrpcService = TPServiceInMemoryForTesting()

  @get:Rule
  val tempFolder = TemporaryFolder()

  @get:Rule
  val fakeGrpcChannel = FakeGrpcChannel("TraceProcessorServiceImplTest", fakeGrpcService)

  @Test
  fun `loadTrace - ok`() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    ideService.loadTrace(10, traceFile)

    val expectedRequest = TraceProcessor.LoadTraceRequest.newBuilder()
      .setTraceId(10)
      .setTracePath(traceFile.absolutePath)
      .build()
    assertThat(fakeGrpcService.lastLoadTraceRequest).isEqualTo(expectedRequest)
  }

  @Test
  fun `loadTrace - fail`() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(false)
      .setError("Testing Failure")
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    try {
      ideService.loadTrace(10, traceFile)
      fail()
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("Error loading trace with TPD: Testing Failure")
    }
  }

  @Test
  fun `loadTrace - grpc retry`() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    fakeGrpcService.failsPerQuery = 2
    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    ideService.loadTrace(10, traceFile)
  }

  @Test
  fun `loadTrace - grpc retry exhausted`() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    fakeGrpcService.failsPerQuery = 5
    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    try {
      ideService.loadTrace(10, traceFile)
      fail()
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("Unable to reach TPDaemon.")
    }
  }

  @Test
  fun loadCpuData() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    // For test simplicity here, will return a single result (the real case would be one for each query in the batch)
    fakeGrpcService.queryBatchResponse = TraceProcessor.QueryBatchResponse.newBuilder()
      .addResult(TraceProcessor.QueryResult.newBuilder().setOk(true))
      .build()

    ideService.loadCpuData(10, listOf(33, 42))

    val expectedRequest = TraceProcessor.QueryBatchRequest.newBuilder()
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setProcessMetadataRequest(TraceProcessor.QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setSchedRequest(TraceProcessor.QueryParameters.SchedulingEventsParameters.getDefaultInstance()))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setTraceEventsRequest(TraceProcessor.QueryParameters.TraceEventsParameters.newBuilder().setProcessId(33)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setCountersRequest(TraceProcessor.QueryParameters.CountersParameters.newBuilder().setProcessId(33)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setTraceEventsRequest(TraceProcessor.QueryParameters.TraceEventsParameters.newBuilder().setProcessId(42)))
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setTraceId(10)
                  .setCountersRequest(TraceProcessor.QueryParameters.CountersParameters.newBuilder().setProcessId(42)))
      .build()
    assertThat(fakeGrpcService.lastQueryBatchRequest).isEqualTo(expectedRequest)
  }

  @Test
  fun `reload trace on data fetch`() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    fakeGrpcService.loadTraceResponse = TraceProcessor.LoadTraceResponse.newBuilder()
      .setOk(true)
      .build()

    val traceFile = tempFolder.newFile("perfetto.trace")
    ideService.loadTrace(10, traceFile)
    fakeGrpcService.lastLoadTraceRequest = null // Mark as a null, so we can verify below it was called

    // For test simplicity here, will return a single result (the real case would be one for each query in the batch)
    fakeGrpcService.queryBatchResponse = TraceProcessor.QueryBatchResponse.newBuilder()
      .addResult(TraceProcessor.QueryResult.newBuilder()
                   .setOk(false)
                   .setFailureReason(TraceProcessor.QueryResult.QueryFailureReason.TRACE_NOT_FOUND))
      .build()

    ideService.loadCpuData(10, listOf(33, 42))
    // Can't do assertThat(...).isNotNull() because of a problem that assertThat(Any?).isNotNull()
    fakeGrpcService.lastLoadTraceRequest ?: fail("Expected lastLoadTraceRequest to not be null")
  }

  @Test
  fun `trace never loaded`() {
    val client = TraceProcessorDaemonClient(TraceProcessorServiceGrpc.newBlockingStub(fakeGrpcChannel.channel))
    val ideService = TraceProcessorServiceImpl(client)

    // For test simplicity here, will return a single result (the real case would be one for each query in the batch)
    fakeGrpcService.queryBatchResponse = TraceProcessor.QueryBatchResponse.newBuilder()
      .addResult(TraceProcessor.QueryResult.newBuilder()
                   .setOk(false)
                   .setFailureReason(TraceProcessor.QueryResult.QueryFailureReason.TRACE_NOT_FOUND))
      .build()

    try {
      ideService.loadCpuData(10, listOf(33, 42))
      fail()
    } catch (e: RuntimeException) {
      assertThat(e.message).isEqualTo("Trace 10 needs to be loaded before querying.")
    }

    // We never issue a load trace since we don't know about the trace.
    assertThat(fakeGrpcService.lastLoadTraceRequest).isNull()
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
}