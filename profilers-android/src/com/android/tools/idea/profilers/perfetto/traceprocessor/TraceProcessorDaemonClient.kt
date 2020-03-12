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
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo
import com.intellij.openapi.diagnostic.Logger
import io.grpc.Channel
import io.grpc.ManagedChannelBuilder
import java.io.File

/**
 * gRPC client used to communicate with the daemon (which runs a gRPC server).
 * For the API details, see {@code tools/base/profiler/native/trace_processor_daemon/trace_processor_service.proto}.
 */
class TraceProcessorDaemonClient(optionalChannel: Channel? = null) {
  // TODO(b/149379691): Use a port picker to select an available port, pass it down to the daemon as an argument and use it here.
  private val channel: Channel by lazy {
    optionalChannel ?: ManagedChannelBuilder.forAddress("localhost", 20204).usePlaintext().build() }
  private val stub: TraceProcessorServiceGrpc.TraceProcessorServiceBlockingStub by lazy {
    TraceProcessorServiceGrpc.newBlockingStub(channel)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorDaemonClient::class.java)
  }

  fun loadTrace(traceId: Long, traceFile: File): List<CpuThreadSliceInfo> {
    val requestProto = TraceProcessor.LoadTraceRequest.newBuilder()
      .setTraceId(traceId)
      .setTracePath(traceFile.absolutePath)
      .build()
    val responseProto = stub.loadTrace(requestProto)
    val threadList = mutableListOf<CpuThreadSliceInfo>()

    for (process in responseProto.processMetadata.processList) {
      for (thread in process.threadList) {
        // We are only interested on the main thread of each process.
        if (thread.id == process.id) {
          threadList.add(
            CpuThreadSliceInfo(thread.id.toInt(), thread.name, process.id.toInt(), process.name))
        }
      }
    }
    return threadList.toList()
  }

  fun queryBatchRequest(request: TraceProcessor.QueryBatchRequest): TraceProcessor.QueryBatchResponse {
    return stub.queryBatch(request)
  }
}