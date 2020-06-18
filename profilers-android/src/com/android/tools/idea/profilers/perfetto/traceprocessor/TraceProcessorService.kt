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
import com.android.tools.profilers.systemtrace.ThreadModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.io.File
import java.lang.RuntimeException

/**
 * See {@link TraceProcessorService} for API details.
 */
@Service
class TraceProcessorServiceImpl : TraceProcessorService, Disposable {
  private val client = TraceProcessorDaemonClient()
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
    val requestProto = TraceProcessor.LoadTraceRequest.newBuilder()
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
    val query = TraceProcessor.QueryBatchRequest.newBuilder()
      // Query metadata for all processes.
      .addQuery(TraceProcessor.QueryParameters.newBuilder().setProcessMetadataRequest(
        TraceProcessor.QueryParameters.ProcessMetadataParameters.getDefaultInstance()))
      .build()

    LOGGER.info("TPD Service: Querying process metadata for trace $traceId.")
    var response = client.queryBatchRequest(query)
    if (response.resultList.any { it.failureReason == TraceProcessor.QueryResult.QueryFailureReason.TRACE_NOT_FOUND}) {
      // Something happened and the trace is not there anymore, let's try to reload it:
      loadTrace(traceId, loadedTraces[traceId] ?: throw RuntimeException("Trace $traceId needs to be loaded before querying."))
      response = client.queryBatchRequest(query)
    }

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

    LOGGER.info("TPD Service: Querying cpu data for trace $traceId.")
    var response = client.queryBatchRequest(queryBuilder.build())
    if (response.resultList.any { it.failureReason == TraceProcessor.QueryResult.QueryFailureReason.TRACE_NOT_FOUND}) {
      // Something happened and the trace is not there anymore, let's try to reload it:
      loadTrace(traceId, loadedTraces[traceId] ?: throw RuntimeException("Trace $traceId needs to be loaded before querying."))
      response = client.queryBatchRequest(queryBuilder.build())
    }

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

  // TODO(b/157743759): Update this to pass the traceId and check if the response is ok like the other methods above.
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

