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
import com.android.tools.profiler.perfetto.proto.TraceProcessor.LoadTraceRequest
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchRequest
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchResponse
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryParameters
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryResult
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
class TraceProcessorServiceImpl(
    private val client: TraceProcessorDaemonClient = TraceProcessorDaemonClient()) : TraceProcessorService, Disposable {
  private val loadedTraces = mutableMapOf<Long, File>()

  init {
    Disposer.register(this, client)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorServiceImpl::class.java)

    @JvmStatic
    fun getInstance(): TraceProcessorService {
      return ServiceManager.getService(TraceProcessorServiceImpl::class.java)
    }
  }

  override fun loadTrace(traceId: Long, traceFile: File) {
    LOGGER.info("TPD Service: Loading trace $traceId: ${traceFile.absolutePath}")
    val requestProto = LoadTraceRequest.newBuilder()
      .setTraceId(traceId)
      .setTracePath(traceFile.absolutePath)
      .build()
    val response = client.loadTrace(requestProto)

    if (!response.ok) {
      LOGGER.info("TPD Service: Fail to load trace $traceId: ${response.error}")
      throw RuntimeException("Error loading trace with TPD: ${response.error}")
    }
    LOGGER.info("TPD Service: Trace $traceId loaded.")

    loadedTraces[traceId] = traceFile
  }

  override fun getProcessMetadata(traceId: Long): List<ProcessModel> {
    val query = QueryBatchRequest.newBuilder()
      // Query metadata for all processes.
      .addQuery(QueryParameters.newBuilder().setProcessMetadataRequest(
        QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      .build()

    LOGGER.info("TPD Service: Querying process metadata for trace $traceId.")
    val response = executeBatchQuery(traceId, query)

    response.resultList.forEach {
      if (!it.ok) {
        LOGGER.warn("TPD Service: Query failed - ${it.failureReason} - ${it.error}")
      }
    }

    val modelBuilder = TraceProcessorModel.Builder()
    response.resultList
      .filter { it.hasProcessMetadataResult() }
      .forEach { modelBuilder.addProcessMetadata(it.processMetadataResult) }
    val model = modelBuilder.build()

    return model.getProcesses()
  }

  override fun loadCpuData(traceId: Long, processIds: List<Int>): SystemTraceModelAdapter {
    val queryBuilder = QueryBatchRequest.newBuilder()
      // Query metadata for all processes, as we need the info from everything to reference in the scheduling events.
      .addQuery(QueryParameters.newBuilder().setProcessMetadataRequest(QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      // Query scheduling for all processes, as we need it to build the cpu/core data series anyway.
      .addQuery(QueryParameters.newBuilder().setSchedRequest(QueryParameters.SchedulingEventsParameters.getDefaultInstance()))

    // Now let's add the queries that we limit for the processes we're interested in:
    for (id in processIds) {
      queryBuilder.addQuery(QueryParameters.newBuilder().setTraceEventsRequest(
        QueryParameters.TraceEventsParameters.newBuilder().setProcessId(id.toLong())))
      queryBuilder.addQuery(QueryParameters.newBuilder().setCountersRequest(
          QueryParameters.CountersParameters.newBuilder().setProcessId(id.toLong())))
    }

    LOGGER.info("TPD Service: Querying cpu data for trace $traceId.")
    val response = executeBatchQuery(traceId, queryBuilder.build())

    response.resultList.forEach {
      if (!it.ok) {
        LOGGER.warn("TPD Service: Query failed - ${it.failureReason} - ${it.error}")
      }
    }

    val modelBuilder = TraceProcessorModel.Builder()
    response.resultList.filter { it.hasProcessMetadataResult() }.forEach { modelBuilder.addProcessMetadata(it.processMetadataResult) }
    response.resultList.filter { it.hasTraceEventsResult() }.forEach { modelBuilder.addTraceEvents(it.traceEventsResult) }
    response.resultList.filter { it.hasSchedResult() }.forEach { modelBuilder.addSchedulingEvents(it.schedResult) }
    response.resultList.filter { it.hasCountersResult() }.forEach { modelBuilder.addCounters(it.countersResult) }

    return modelBuilder.build()
  }

  override fun loadMemoryData(traceId: Long, abi: String, symbolizer: NativeFrameSymbolizer, memorySet: NativeMemoryHeapSet) {
    val converter = HeapProfdConverter(abi, symbolizer, memorySet, WindowsNameDemangler())
    val query = QueryBatchRequest.newBuilder()
      .addQuery(QueryParameters.newBuilder().setMemoryRequest(Memory.AllocationDataRequest.getDefaultInstance()).build())
      .build()

    LOGGER.info("TPD Service: Querying process metadata for trace $traceId.")
    val response = executeBatchQuery(traceId, query)

    response.resultList.stream().filter { it.hasMemoryEvents() }.forEach {
      converter.populateHeapSet(it.memoryEvents)
    }
  }

  /**
   * Execute {@code query} on TPD, reloading the trace if has been unloaded (e.g. TPD crashed between loading and the query request).
   */
  private fun executeBatchQuery(traceId: Long, query: QueryBatchRequest): QueryBatchResponse {
    var response = client.queryBatchRequest(query)
    if (response.resultList.any { it.failureReason == QueryResult.QueryFailureReason.TRACE_NOT_FOUND}) {
      // Something happened and the trace is not there anymore, let's try to reload it:
      loadTrace(traceId, loadedTraces[traceId] ?: throw RuntimeException("Trace $traceId needs to be loaded before querying."))
      response = client.queryBatchRequest(query)
    }
    return response
  }

  override fun dispose() {}
}

