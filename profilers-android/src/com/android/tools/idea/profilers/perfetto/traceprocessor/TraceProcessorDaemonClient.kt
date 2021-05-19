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

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessorServiceGrpc
import com.android.tools.profilers.analytics.FeatureTracker
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Ticker
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit

/**
 * gRPC client used to communicate with the daemon (which runs a gRPC server).
 * For the API details, see {@code tools/base/profiler/native/trace_processor_daemon/trace_processor_service.proto}.
 */
class TraceProcessorDaemonClient(ticker: Ticker): Disposable {

  @VisibleForTesting
  constructor(ticker: Ticker, stubForTesting: TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub? = null): this(ticker) {
    this.stubForTesting = stubForTesting
  }

  private val daemonManager = TraceProcessorDaemonManager(ticker)
  private var cachedChannelPort = 0
  private var cachedChannel: ManagedChannel? = null
  private var cachedStub: TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub? = null
  private var stubForTesting: TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub? = null
  // Controls if we started the dispose process for this manager, to prevent new instances of daemon to be spawned.
  private var disposed = false

  init {
    Disposer.register(this, daemonManager)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorDaemonClient::class.java)
  }

  @Synchronized
  private fun getStub(tracker: FeatureTracker): TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub {
    // If we have a stub for testing, just return it.
    stubForTesting?.let { return it }

    daemonManager.makeSureDaemonIsRunning(tracker)
    val previousChannel = cachedChannel
    // If we either don't have a channel created already of if it has been broken, we must create a new one.
    if (previousChannel == null || previousChannel.isShutdown || previousChannel.isTerminated
        || cachedChannelPort != daemonManager.daemonPort) {
      // If we had a channel, let's make sure we shutdown it properly before creating a new one.
      previousChannel?.shutdownNow()

      // Lets set up the new channel now
      cachedChannelPort = daemonManager.daemonPort
      LOGGER.debug("TPD Client: building new channel to localhost:$cachedChannelPort")
      cachedChannel = ManagedChannelBuilder.forAddress("localhost", cachedChannelPort)
        .usePlaintext()
        .maxInboundMessageSize(512 * 1024 * 1024) // 512 Mb
        .build()
    }

    // If we still have no stub or if we changed our channel, we need to update out stub.
    if (cachedStub == null || previousChannel != cachedChannel) {
      LOGGER.debug("TPD Client: building new stub")
      cachedStub = TraceProcessorServiceGrpc.newBlockingStub(cachedChannel)
    }

    return cachedStub!!
  }

  fun loadTrace(requestProto: TraceProcessor.LoadTraceRequest,
                tracker: FeatureTracker): TraceProcessorDaemonQueryResult<TraceProcessor.LoadTraceResponse> {
    return retry(requestProto) { getStub(tracker).loadTrace(it) }
  }

  fun queryBatchRequest(request: TraceProcessor.QueryBatchRequest,
                        tracker: FeatureTracker): TraceProcessorDaemonQueryResult<TraceProcessor.QueryBatchResponse> {
    return retry(request) { getStub(tracker).queryBatch(it) }
  }

  // Retry the same call up to 3 times, if all of them fail rethrow the last exception.
  // In between retries, sleep for 200ms, to allow the underlying issue to fix itself.
  private fun <A, B> retry(request: A, rpc: (A) -> B): TraceProcessorDaemonQueryResult<B> {
    var lastException: Exception? = null
    for(i in 1..3){
      try {
        if (!disposed) {
          return TraceProcessorDaemonQueryResult(rpc(request))
        }
      } catch (e: Exception) {
        LOGGER.debug("TPD Client: Attempt $i of RPC failed (`${e.message}`).")
        lastException = e
        Thread.sleep(200)
      }
    }

    // If we arrived here, is because we never managed to return the rpc request above.
    return TraceProcessorDaemonQueryResult(RuntimeException("Unable to reach TPDaemon.", lastException))
  }

  override fun dispose() {
    disposed = true
    cachedChannel?.shutdownNow()
  }
}

/**
 * Wrapper for the result of a query sent to TPD, that can contain the response received (if {@code completed} is true) or the reason
 * why it failed to contact TPD in {@code failure}.
 */
data class TraceProcessorDaemonQueryResult<A> private constructor(
  val response: A? = null,
  val failure: Exception? = null) {

  val completed = response != null

  constructor(response: A): this(response, null) { }
  constructor(failure: Exception): this(null, failure) { }
}