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
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import java.lang.RuntimeException

/**
 * gRPC client used to communicate with the daemon (which runs a gRPC server).
 * For the API details, see {@code tools/base/profiler/native/trace_processor_daemon/trace_processor_service.proto}.
 */
class TraceProcessorDaemonClient(val optionalChannel: ManagedChannel? = null): Disposable {
  private val daemonManager = TraceProcessorDaemonManager()
  private var cachedChannel: ManagedChannel? = null
  private var cachedStub: TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub? = null

  init {
    Disposer.register(this, daemonManager)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorDaemonClient::class.java)
  }

  private fun getStub(): TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub {
    val previousChannel = cachedChannel
    // If we either don't have a channel created already of if it has been broken, we must create a new one.
    if (previousChannel == null || previousChannel.isShutdown || previousChannel.isTerminated) {
      // TODO(b/149379691): Use a port picker to select an available port, pass it down to the daemon as an argument and use it here.
      LOGGER.debug("TPD Client: building new channel")
      cachedChannel = optionalChannel ?: ManagedChannelBuilder.forAddress("localhost", 20204)
        .usePlaintext()
        .maxInboundMessageSize(128 * 1024 * 1024) // 128 Mb
        .build()
    }

    // If we still have no stub or if we changed our channel, we need to update out stub.
    if (cachedStub == null || previousChannel != cachedChannel) {
      LOGGER.debug("TPD Client: building new stub")
      cachedStub = TraceProcessorServiceGrpc.newBlockingStub(cachedChannel)
    }

    return cachedStub!!
  }

  fun loadTrace(requestProto: TraceProcessor.LoadTraceRequest): TraceProcessor.LoadTraceResponse {
    return retry(requestProto) { getStub().loadTrace(it) }
  }

  fun queryBatchRequest(request: TraceProcessor.QueryBatchRequest): TraceProcessor.QueryBatchResponse {
    return retry(request) { getStub().queryBatch(it)}
  }

  // Retry the same call up to 3 times, if all of them fail rethrow the last exception.
  // In between retries, sleep for 200ms, to allow the underlying issue to fix itself.
  private fun <A, B> retry(request: A, rpc: (A) -> B): B {
    var response: B? = null
    var lastException: Exception? = null
    for(i in 1..3){
      try {
        daemonManager.makeSureDaemonIsRunning()
        lastException = null
        response = rpc(request)
        break
      } catch (e: Exception) {
        LOGGER.debug("TPD Client: Attempt $i of RPC failed (`${e.message}`).")
        lastException = e
        Thread.sleep(200)
      }
    }

    if (response == null) {
      throw RuntimeException("Unable to reach TPDaemon.", lastException)
    }
    return response
  }

  override fun dispose() {
    cachedChannel?.shutdownNow()
  }
}