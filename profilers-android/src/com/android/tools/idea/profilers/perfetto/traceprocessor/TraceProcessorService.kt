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

import com.android.tools.profiler.perfetto.proto.Memory
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchResponse
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.io.File

/**
 * See {@link TraceProcessorService} for API details.
 */
@Service
class TraceProcessorServiceImpl : TraceProcessorService, Disposable {
  private val daemonManager = TraceProcessorDaemonManager()
  private val client = TraceProcessorDaemonClient()

  init {
    Disposer.register(this, daemonManager)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorServiceImpl::class.java)

    @JvmStatic
    fun getInstance(): TraceProcessorService {
      return ServiceManager.getService(TraceProcessorServiceImpl::class.java)
    }
  }

  override fun loadTrace(traceId: Long, traceFile: File): List<ProcessModel> {
    daemonManager.makeSureDaemonIsRunning()
    return client.loadTrace(traceId, traceFile)
  }

  override fun loadCpuData(traceId: Long, processIds: List<Int>): SystemTraceModelAdapter {
    val queryBuilder = TraceProcessor.QueryBatchRequest.newBuilder()
      // Query metadata for all processes, as we need the info from everything to reference in the scheduling events.
      .addQuery(TraceProcessor.QueryParameters.newBuilder().setProcessMetadataRequest(
        TraceProcessor.QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      // Query scheduling for all processes, as we need it to build the cpu/core data series anyway.
      .addQuery(TraceProcessor.QueryParameters.newBuilder().setSchedRequest(
        TraceProcessor.QueryParameters.SchedulingEventsParameters.getDefaultInstance()))

    // Now let's add the queries that we limit for the processes we're interested in:
    for (id in processIds) {
      queryBuilder.addQuery(TraceProcessor.QueryParameters.newBuilder().setTraceEventsRequest(
        TraceProcessor.QueryParameters.TraceEventsParameters.newBuilder().setProcessId(id.toLong())))
      queryBuilder.addQuery(TraceProcessor.QueryParameters.newBuilder().setCountersRequest(
          TraceProcessor.QueryParameters.CountersParameters.newBuilder().setProcessId(id.toLong())))
    }

    daemonManager.makeSureDaemonIsRunning()
    val response = client.queryBatchRequest(queryBuilder.build())

    val modelBuilder = TraceProcessorModel.Builder()
    response.resultList.filter { it.hasProcessMetadataResult() }.forEach { modelBuilder.addProcessMetadata(it.processMetadataResult) }
    response.resultList.filter { it.hasTraceEventsResult() }.forEach { modelBuilder.addTraceEvents(it.traceEventsResult) }
    response.resultList.filter { it.hasSchedResult() }.forEach { modelBuilder.addSchedulingEvents(it.schedResult) }
    response.resultList.filter { it.hasCountersResult() }.forEach { modelBuilder.addCounters(it.countersResult) }

    return modelBuilder.build()
  }

  override fun loadMemoryData(abi: String, symbolizer: NativeFrameSymbolizer, memorySet: NativeMemoryHeapSet) {
    val converter = HeapProfdConverter(abi, symbolizer, memorySet, WindowsNameDemangler())
    val request = TraceProcessor.QueryBatchRequest.newBuilder()
      .addQuery(TraceProcessor.QueryParameters.newBuilder()
                  .setMemoryRequest(Memory.AllocationDataRequest.getDefaultInstance()).build())
      .build()
    val response: QueryBatchResponse = client.queryBatchRequest(request)
    response.resultList.stream().filter { it.hasMemoryEvents() }.forEach {
      converter.populateHeapSet(it.memoryEvents)
    }
  }

  override fun dispose() {}
}

