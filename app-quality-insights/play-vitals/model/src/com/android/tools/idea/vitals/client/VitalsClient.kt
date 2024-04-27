/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.client

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.DetailedIssueStats
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventPage
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.MINIMUM_PERCENTAGE_TO_SHOW
import com.android.tools.idea.insights.MINIMUM_SUMMARY_GROUP_SIZE_TO_SHOW
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.AppInsightsCache
import com.android.tools.idea.insights.client.AppInsightsClient
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.client.QueryFilters
import com.android.tools.idea.insights.client.runGrpcCatching
import com.android.tools.idea.insights.summarizeDevicesFromRawDataPoints
import com.android.tools.idea.insights.summarizeOsesFromRawDataPoints
import com.android.tools.idea.vitals.client.grpc.VitalsGrpcClient
import com.android.tools.idea.vitals.client.grpc.VitalsGrpcClientImpl
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.DimensionsAndMetrics
import com.android.tools.idea.vitals.datamodel.MetricType
import com.android.tools.idea.vitals.datamodel.extractValue
import com.android.tools.idea.vitals.datamodel.fromDimensions
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val NOT_SUPPORTED_ERROR_MSG = "Vitals doesn't support this."
private const val MAX_CONCURRENT_CALLS = 10

class VitalsClient(
  parentDisposable: Disposable,
  private val cache: AppInsightsCache,
  private val grpcClient: VitalsGrpcClient = VitalsGrpcClientImpl.create(parentDisposable)
) : AppInsightsClient {
  private val concurrentCallLimit = Semaphore(MAX_CONCURRENT_CALLS)

  override suspend fun listConnections(): LoadingState.Done<List<AppConnection>> = supervisorScope {
    runGrpcCatching(notFoundFallbackValue = LoadingState.Ready(emptyList())) {
      LoadingState.Ready(grpcClient.listAccessibleApps())
    }
  }

  override suspend fun listTopOpenIssues(
    request: IssueRequest,
    fetchSource: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource?,
    mode: ConnectionMode,
    permission: Permission
  ): LoadingState.Done<IssueResponse> = supervisorScope {
    runGrpcCatching(
      notFoundFallbackValue =
        LoadingState.Ready(
          IssueResponse(
            issues = emptyList(),
            versions = emptyList(),
            devices = emptyList(),
            operatingSystems = emptyList(),
            permission = Permission.NONE
          )
        )
    ) {
      if (mode.isOfflineMode()) {
        val topCachedIssues = cache.getTopIssues(request) ?: emptyList()
        return@runGrpcCatching LoadingState.Ready(
          IssueResponse(topCachedIssues, emptyList(), emptyList(), emptyList(), Permission.FULL)
        )
      }
      val versions = async {
        listVersions(
          request.connection,
          request.filters.copy(versions = setOf(Version.ALL)),
          null,
          MetricType.ERROR_REPORT_COUNT
        )
      }
      val devices = async {
        listDevices(
          request.connection,
          request.filters.copy(devices = setOf(Device.ALL)),
          null,
          MetricType.ERROR_REPORT_COUNT
        )
      }
      val oses = async {
        listOperatingSystems(
          request.connection,
          request.filters.copy(operatingSystems = setOf(OperatingSystemInfo.ALL)),
          null,
          MetricType.ERROR_REPORT_COUNT
        )
      }
      val issues = async {
        fetchIssues(
          request,
          fetchSource ==
            AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource.REFRESH
        )
      }

      LoadingState.Ready(
        IssueResponse(
          issues.await(),
          versions.await(),
          devices.await(),
          oses.await(),
          Permission.READ_ONLY
        )
      )
    }
  }

  override suspend fun getIssueVariants(request: IssueRequest, issueId: IssueId) =
    LoadingState.Ready(emptyList<IssueVariant>())

  override suspend fun getIssueDetails(
    issueId: IssueId,
    request: IssueRequest,
    variantId: String?
  ): LoadingState.Done<DetailedIssueStats?> = supervisorScope {
    val failure = LoadingState.UnknownFailure("Unable to fetch issue details.")
    runGrpcCatching(failure) {
      val devices = async {
        listDevices(request.connection, request.filters, issueId, MetricType.DISTINCT_USER_COUNT)
          .summarizeDevicesFromRawDataPoints(
            MINIMUM_SUMMARY_GROUP_SIZE_TO_SHOW,
            MINIMUM_PERCENTAGE_TO_SHOW
          )
      }

      val oses = async {
        listOperatingSystems(
            request.connection,
            request.filters,
            issueId,
            MetricType.DISTINCT_USER_COUNT
          )
          .summarizeOsesFromRawDataPoints(
            MINIMUM_SUMMARY_GROUP_SIZE_TO_SHOW,
            MINIMUM_PERCENTAGE_TO_SHOW
          )
      }
      LoadingState.Ready(DetailedIssueStats(devices.await(), oses.await()))
    }
  }

  override suspend fun listEvents(
    issueId: IssueId,
    variantId: String?,
    request: IssueRequest,
    token: String?
  ): LoadingState.Done<EventPage> = LoadingState.Ready(EventPage.EMPTY)

  override suspend fun updateIssueState(
    connection: Connection,
    issueId: IssueId,
    state: IssueState
  ): LoadingState.Done<Unit> {
    throw UnsupportedOperationException(NOT_SUPPORTED_ERROR_MSG)
  }

  override suspend fun listNotes(
    connection: Connection,
    issueId: IssueId,
    mode: ConnectionMode
  ): LoadingState.Done<List<Note>> {
    return LoadingState.Ready(emptyList())
  }

  override suspend fun createNote(
    connection: Connection,
    issueId: IssueId,
    message: String
  ): LoadingState.Done<Note> {
    throw UnsupportedOperationException(NOT_SUPPORTED_ERROR_MSG)
  }

  override suspend fun deleteNote(connection: Connection, id: NoteId): LoadingState.Done<Unit> {
    throw UnsupportedOperationException(NOT_SUPPORTED_ERROR_MSG)
  }

  private suspend fun fetchIssues(
    request: IssueRequest,
    fetchEventsForAllIssues: Boolean = false
  ): List<AppInsightsIssue> = coroutineScope {
    val topIssues = grpcClient.listTopIssues(request.connection, request.filters)

    val (requestIssues, cachedSampleEvents) =
      if (fetchEventsForAllIssues) {
        topIssues to emptyMap()
      } else {
        val cachedSampleEvents =
          topIssues
            .mapNotNull { cache.getEvent(request, it.id)?.let { event -> it to event } }
            .toMap()
        topIssues.filterNot { it in cachedSampleEvents.keys } to cachedSampleEvents
      }

    // TODO: revisit once we have a new API.
    val requestedEventsByIssue =
      requestIssues
        .map { issueDetails ->
          async {
            // Here we restrict number of concurrent calls as a short-term fix while waiting for
            // b/287461357 being resolved. Limiting calls are to address high chances of
            // 'UNAVAILABLE' errors to some extent.
            val reports =
              concurrentCallLimit.withPermit {
                grpcClient.searchErrorReports(
                  request.connection,
                  request.filters,
                  issueDetails.id,
                  1
                )
              }

            reports.firstOrNull()
              ?: Event.EMPTY.also {
                thisLogger().warn("No sample report got for $issueDetails by request: $request.")
              }
          }
        }
        .awaitAll()
        .mapIndexed { index, event -> requestIssues[index] to event }
        .toMap()

    return@coroutineScope topIssues
      .map { issueDetails ->
        AppInsightsIssue(
          issueDetails,
          cachedSampleEvents[issueDetails] ?: requestedEventsByIssue[issueDetails]!!
        )
      }
      .also { cache.populateIssues(request.connection, it) }
  }

  private suspend fun listVersions(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId?,
    metricType: MetricType
  ): List<WithCount<Version>> {
    // First we get versions that are part of the releases/tracks.
    val releases = grpcClient.getReleases(connection)

    // Next we get all versions from the "metrics" call. And then we are able to combine the both
    // info to build up the [Version] list.
    return getMetrics(
        connection = connection,
        filters = filters,
        issueId = issueId,
        dimensions = listOf(DimensionType.REPORT_TYPE, DimensionType.VERSION_CODE),
        metrics = listOf(metricType)
      )
      .map { dataPoint ->
        val version =
          Version.fromDimensions(dataPoint.dimensions).let { rawVersion ->
            val tracks =
              releases
                .singleOrNull { release -> release.buildVersion == rawVersion.buildVersion }
                ?.tracks ?: emptySet()
            rawVersion.copy(tracks = tracks)
          }

        val count = dataPoint.metrics.extractValue(metricType)

        version to count
      }
      .aggregateToWithCount()
      .sortedByDescending { it.value.buildVersion }
  }

  private suspend fun listDevices(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId?,
    metricType: MetricType
  ): List<WithCount<Device>> {
    return getMetrics(
        connection = connection,
        filters = filters,
        issueId = issueId,
        dimensions =
          listOf(
            DimensionType.REPORT_TYPE,
            DimensionType.DEVICE_BRAND,
            DimensionType.DEVICE_MODEL,
            DimensionType.DEVICE_TYPE
          ),
        metrics = listOf(metricType)
      )
      .map { dataPoint ->
        val device = Device.fromDimensions(dataPoint.dimensions)
        val count = dataPoint.metrics.extractValue(metricType)

        device to count
      }
      .aggregateToWithCount()
      .sortedByDescending { it.count }
  }

  private suspend fun listOperatingSystems(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId?,
    metricType: MetricType
  ): List<WithCount<OperatingSystemInfo>> {
    return getMetrics(
        connection = connection,
        filters = filters,
        issueId = issueId,
        dimensions = listOf(DimensionType.REPORT_TYPE, DimensionType.API_LEVEL),
        metrics = listOf(metricType)
      )
      .map { dataPoint ->
        val os = OperatingSystemInfo.fromDimensions(dataPoint.dimensions)
        val count = dataPoint.metrics.extractValue(metricType)

        os to count
      }
      .aggregateToWithCount()
      .sortedByDescending { it.count }
  }

  private suspend fun getMetrics(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId?,
    dimensions: List<DimensionType>,
    metrics: List<MetricType>
  ): List<DimensionsAndMetrics> {
    val freshness =
      grpcClient.getErrorCountMetricsFreshnessInfo(connection).maxByOrNull { it.timeGranularity }
        ?: throw IllegalStateException("No freshness info found for app: ${connection.appId}.")

    return grpcClient.queryErrorCountMetrics(
      connection,
      filters,
      issueId,
      dimensions,
      metrics,
      freshness
    )
  }
}

internal fun <T> List<Pair<T, Long>>.aggregateToWithCount(): List<WithCount<T>> {
  return fold(mutableMapOf<T, Long>()) { acc, pair ->
      acc[pair.first] = (acc[pair.first] ?: 0L) + pair.second
      acc
    }
    .map { (version, count) -> WithCount(count = count, value = version) }
}
