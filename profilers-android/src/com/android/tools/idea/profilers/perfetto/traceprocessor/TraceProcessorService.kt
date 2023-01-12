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

import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.profiler.perfetto.proto.Memory
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessor.LoadTraceRequest
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchRequest
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryBatchResponse
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryParameters
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryResult
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.cpu.systemtrace.SystemTraceSurfaceflingerManager
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorModel
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * See {@link TraceProcessorService} for API details.
 */
@Service
class TraceProcessorServiceImpl(
  private val ticker: Ticker = Ticker.systemTicker(),
  private val client: TraceProcessorDaemonClient = TraceProcessorDaemonClient(ticker)) : TraceProcessorService, Disposable {
  private val loadedTraces = mutableMapOf<Long, File>()

  init {
    Disposer.register(this, client)
  }

  companion object {
    private val LOGGER = Logger.getInstance(TraceProcessorServiceImpl::class.java)

    @JvmStatic
    fun getInstance(): TraceProcessorService {
      return ApplicationManager.getApplication().getService(TraceProcessorServiceImpl::class.java)
    }

    /**
     * Creates request builders for querying CPU data, and a model to collect the result
     * when these requests are executed.
     */
    private fun cpuDataRequest(processes: List<ProcessModel>,
                               selectedProcess: ProcessModel,
                               modelBuilder: TraceProcessorModel.Builder,
                               systemTracePowerProfilerDisplayMode: PowerProfilerDisplayMode): List<RequestBuilder> {
      fun androidFrameTimelineRequest(id: Long, handle: (TraceProcessor.AndroidFrameTimelineResult) -> Unit ) =
        RequestBuilder({ setAndroidFrameTimelineRequest(
          QueryParameters.AndroidFrameTimelineParameters.newBuilder().setProcessId(id))},
                       { handle(it.androidFrameTimelineResult) })
      val requests = mutableListOf(
        // Query metadata for all processes, as we need the info from everything to reference in the scheduling events.
        RequestBuilder({ processMetadataRequest = QueryParameters.ProcessMetadataParameters.getDefaultInstance() },
                       { modelBuilder.addProcessMetadata(it.processMetadataResult) }),
        // Query scheduling for all processes, as we need it to build the cpu/core data series anyway.
        RequestBuilder({ schedRequest = QueryParameters.SchedulingEventsParameters.getDefaultInstance() },
                       { modelBuilder.addSchedulingEvents(it.schedResult) }),
        // Query all CPU data.
        RequestBuilder({ cpuCoreCountersRequest = QueryParameters.CpuCoreCountersParameters.getDefaultInstance() },
                       { modelBuilder.addCpuCounters(it.cpuCoreCountersResult) }),
        // Query Android frame events.
        // Use the selected process name as layer name hint, e.g. com.example.app/MainActivity#0.
        RequestBuilder({ setAndroidFrameEventsRequest(
          QueryParameters.AndroidFrameEventsParameters.newBuilder().setLayerNameHint(selectedProcess.name) )},
                       { modelBuilder.addAndroidFrameEvents(it.androidFrameEventsResult) }),
        // Query for power rail and battery drain data.
        RequestBuilder({
                         powerCounterTracksRequest = QueryParameters.PowerCounterTracksParameters.newBuilder()
                           .setDisplayMode(systemTracePowerProfilerDisplayMode.value).build()
                       },
                       { modelBuilder.addPowerCounters(it.powerCounterTracksResult) }),
        // Query Android FrameTimeline events.
        androidFrameTimelineRequest(selectedProcess.id.toLong(), modelBuilder::addAndroidFrameTimelineEvents)
      )

      processes.find {
        it.getSafeProcessName().endsWith(SystemTraceSurfaceflingerManager.SURFACEFLINGER_PROCESS_NAME)
      }?.id?.let { surfaceflingerId ->
        requests.add(androidFrameTimelineRequest(surfaceflingerId.toLong(),
                                                 modelBuilder::indexSurfaceflingerFrameTimelineEvents))
      }

      // Now let's add the queries that we limit for the processes we're interested in:
      for (id in processes.map { it.id }) {
        requests.add(RequestBuilder({
                                      setTraceEventsRequest(
                                        QueryParameters.TraceEventsParameters.newBuilder().setProcessId(id.toLong())) },
                                    { modelBuilder.addTraceEvents(it.traceEventsResult) }))
        requests.add(RequestBuilder({
                                      setProcessCountersRequest(
                                        QueryParameters.ProcessCountersParameters.newBuilder().setProcessId(id.toLong())) },
                                    { modelBuilder.addProcessCounters(it.processCountersResult) }))
      }
      return requests
    }

    @VisibleForTesting
    fun buildCpuDataRequestProto(traceId: Long, processes: List<ProcessModel>, selectedProcess: ProcessModel): QueryBatchRequest =
      buildBatchQuery(traceId, cpuDataRequest(processes, selectedProcess, TraceProcessorModel.Builder(), PowerProfilerDisplayMode.HIDE))

    private fun buildBatchQuery(traceId: Long, requestBuilders: List<RequestBuilder>): QueryBatchRequest =
      with(QueryBatchRequest.newBuilder()) {
        requestBuilders.forEach { (setUp, _) -> addQuery(QueryParameters.newBuilder().setTraceId(traceId).apply(setUp)) }
        build()
      }
  }

  override fun loadTrace(traceId: Long, traceFile: File, ideProfilerServices: IdeProfilerServices): Boolean {
    // load trace had no business logic in Java side, so we use a single stopwatch to track both query and method timings.
    val stopwatch = Stopwatch.createStarted(ticker)
    val symbolPaths = ideProfilerServices.nativeSymbolsDirectories
    LOGGER.info("TPD Service: Loading trace $traceId: ${traceFile.absolutePath}")
    val symbolsFile = File("${FileUtil.getTempDirectory()}${File.separator}$traceId.symbols")
    symbolsFile.deleteOnExit()
    val requestProto = LoadTraceRequest.newBuilder()
      .setTraceId(traceId)
      .setTracePath(traceFile.absolutePath)
      .addAllSymbolPath(symbolPaths)
      .setSymbolizedOutputPath(symbolsFile.absolutePath)
      .build()

    val queryResult = client.loadTrace(requestProto, ideProfilerServices.featureTracker)
    stopwatch.stop()

    val queryTimeMs = stopwatch.elapsed(TimeUnit.MILLISECONDS)
    val traceSizeBytes = traceFile.length()

    if (!queryResult.completed) {
      ideProfilerServices.featureTracker.trackTraceProcessorLoadTrace(TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED,
                                                                      queryTimeMs,
                                                                      queryTimeMs,
                                                                      traceSizeBytes)
      val failureReason = queryResult.failure!!
      LOGGER.warn("TPD Service: Fail to load trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to load trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!

    val queryStatus =
      if (response.ok) TraceProcessorDaemonQueryStats.QueryReturnStatus.OK
      else TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR

    ideProfilerServices.featureTracker.trackTraceProcessorLoadTrace(queryStatus, queryTimeMs, queryTimeMs, traceSizeBytes)
    if (response.ok) {
      LOGGER.info("TPD Service: Trace $traceId loaded.")
      loadedTraces[traceId] = traceFile
      return true
    }
    else {
      LOGGER.info("TPD Service: Error loading trace $traceId: ${response.error}")
      return false
    }
  }

  override fun getProcessMetadata(traceId: Long, ideProfilerServices: IdeProfilerServices): List<ProcessModel> =
    TraceProcessorModel.Builder().let { modelBuilder ->
      handleRequest(traceId, ideProfilerServices, FeatureTracker::trackTraceProcessorProcessMetadata,
                    RequestBuilder({ processMetadataRequest = QueryParameters.ProcessMetadataParameters.getDefaultInstance() },
                             { modelBuilder.addProcessMetadata(it.processMetadataResult) }))
      modelBuilder.build().getProcesses()
    }

  override fun loadCpuData(traceId: Long,
                           processes: List<ProcessModel>,
                           selectedProcess: ProcessModel,
                           ideProfilerServices: IdeProfilerServices): SystemTraceModelAdapter =
    TraceProcessorModel.Builder().also { modelBuilder ->
      val requests = cpuDataRequest(processes, selectedProcess, modelBuilder,
                                    ideProfilerServices.featureConfig.systemTracePowerProfilerDisplayMode)
      handleRequest(traceId, ideProfilerServices, FeatureTracker::trackTraceProcessorCpuData, *requests.toTypedArray())
    }.build()

  override fun loadMemoryData(traceId: Long,
                              abi: String,
                              memorySet: NativeMemoryHeapSet,
                              ideProfilerServices: IdeProfilerServices) {
    val converter = HeapProfdConverter(abi, memorySet, WindowsNameDemangler())
    handleRequest(traceId, ideProfilerServices, FeatureTracker::trackTraceProcessorMemoryData,
                  RequestBuilder({ memoryRequest = Memory.AllocationDataRequest.getDefaultInstance() },
                                 { converter.populateHeapSet(it.memoryEvents)}))
  }

  /**
   * Execute a batch request, handling each result.
   *
   * The implementation assumes the batch's results come in the same order
   * as the requests.
   */
  private fun handleRequest(traceId: Long,
                            ideProfilerServices: IdeProfilerServices,
                            trackFeature: (FeatureTracker, TraceProcessorDaemonQueryStats.QueryReturnStatus, Long, Long) -> Unit,
                            vararg requestBuilders: RequestBuilder) {
    val methodStopwatch = Stopwatch.createStarted(ticker)
    val queryProto = buildBatchQuery(traceId, requestBuilders.asList())

    LOGGER.info("TPD Service: Querying cpu data for trace $traceId.")
    val queryStopwatch = Stopwatch.createStarted(ticker)
    val queryResult = executeBatchQuery(traceId, queryProto, ideProfilerServices)
    queryStopwatch.stop()
    val queryTimeMs = queryStopwatch.elapsed(TimeUnit.MILLISECONDS)

    if (!queryResult.completed) {
      methodStopwatch.stop()
      val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
      trackFeature(ideProfilerServices.featureTracker, TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_FAILED,
                   methodTimeMs, queryTimeMs)
      val failureReason = queryResult.failure!!
      LOGGER.info("TPD Service: Fail to get cpu data for trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to get cpu data for trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!
    var queryError = false
    response.resultList.forEach {
      if (!it.ok) {
        queryError = true
        LOGGER.warn("TPD Service: Load cpu data query error - ${it.failureReason} - ${it.error}")
      }
    }

    (response.resultList zip requestBuilders).forEach { (result, request) -> request.handle(result) }

    // Report metrics for OK or ERROR query
    methodStopwatch.stop()
    val methodTimeMs = methodStopwatch.elapsed(TimeUnit.MILLISECONDS)
    val queryStatus =
      if (queryError) TraceProcessorDaemonQueryStats.QueryReturnStatus.QUERY_ERROR
      else TraceProcessorDaemonQueryStats.QueryReturnStatus.OK
    trackFeature(ideProfilerServices.featureTracker, queryStatus, methodTimeMs, queryTimeMs)
  }

  override fun getTraceMetadata(traceId: Long, metadataName: String, ideProfilerServices: IdeProfilerServices): List<String> {
    val query = QueryBatchRequest.newBuilder()
      // Query metadata by name.
      .addQuery(QueryParameters.newBuilder()
                  .setTraceId(traceId)
                  .setTraceMetadataRequest(QueryParameters.TraceMetadataParameters.newBuilder()
                                             .setName(metadataName)
                                             // Currently we only care about "metadata" type elements. All rows observed with a trace
                                             // captured by studio had this field set to metadata.
                                             .setType("metadata").build()))
      .build()

    LOGGER.info("TPD Service: Querying trace metadata for trace $traceId.")
    val queryResult = executeBatchQuery(traceId, query, ideProfilerServices)

    if (!queryResult.completed) {
      val failureReason = queryResult.failure!!
      LOGGER.warn("TPD Service: Fail to get trace metadata for trace $traceId: ${failureReason.message}")
      throw RuntimeException("TPD Service: Fail to get trace metadata for trace $traceId: ${failureReason.message}", failureReason)
    }

    val response = queryResult.response!!
    val results = mutableListOf<String>()
    response.resultList.forEach { result ->
      if (!result.ok) {
        LOGGER.warn("TPD Service: Trace metadata query error - ${result.failureReason} - ${result.error}")
      }
      if (result.hasTraceMetadataResult()) {
        result.traceMetadataResult.metadataRowList.forEach {
          results.add(it.stringValue ?: it.int64Value.toString())
        }
      }
    }
    return results
  }

  /**
   * Execute {@code query} on TPD, reloading the trace if has been unloaded (e.g. TPD crashed between loading and the query request).
   */
  private fun executeBatchQuery(traceId: Long,
                                query: QueryBatchRequest,
                                ideProfilerServices: IdeProfilerServices): TraceProcessorDaemonQueryResult<QueryBatchResponse> {
    var queryResult = client.queryBatchRequest(query, ideProfilerServices.featureTracker)

    // If we got a response from TPD, we check if TPD could execute the query correctly or if there was any error we can try to
    // recover from, like for example when the trace was not loaded.
    if (queryResult.response?.resultList?.any { it.failureReason == QueryResult.QueryFailureReason.TRACE_NOT_FOUND } == true) {
      val loadedTrace = loadedTraces[traceId]
      if (loadedTrace != null) {
        // We loaded this trace before, but something happened and the trace is not there anymore. Let's try to reload it:
        loadTrace(traceId, loadedTrace, ideProfilerServices)
        queryResult = client.queryBatchRequest(query, ideProfilerServices.featureTracker)
      }
      else {
        // If we don't know about the target trace we're trying to query against, we replace the result with a failed one.
        return TraceProcessorDaemonQueryResult(
          IllegalStateException("Trace $traceId needs to be loaded before querying."))
      }
    }
    return queryResult
  }

  override fun dispose() {}

  private data class RequestBuilder(val setUpQuery: QueryParameters.Builder.() -> Unit,
                                    val handle: (QueryResult) -> Unit)
}
