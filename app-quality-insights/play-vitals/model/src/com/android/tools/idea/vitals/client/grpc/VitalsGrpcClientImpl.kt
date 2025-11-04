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
package com.android.tools.idea.vitals.client.grpc

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.StackTraceGroupParser
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.client.QueryFilters
import com.android.tools.idea.insights.client.retryRpc
import com.android.tools.idea.insights.model.connection.AppConnection
import com.android.tools.idea.vitals.datamodel.Dimension
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.DimensionsAndMetrics
import com.android.tools.idea.vitals.datamodel.FilterBuilder
import com.android.tools.idea.vitals.datamodel.Freshness
import com.android.tools.idea.vitals.datamodel.Metric
import com.android.tools.idea.vitals.datamodel.MetricType
import com.android.tools.idea.vitals.datamodel.TimeGranularity
import com.android.tools.idea.vitals.datamodel.extract
import com.android.tools.idea.vitals.datamodel.toIssueDetails
import com.android.tools.idea.vitals.datamodel.toProto
import com.android.tools.idea.vitals.datamodel.toSampleEvent
import com.google.play.developer.reporting.AggregationPeriod
import com.google.play.developer.reporting.ReportingServiceGrpcKt
import com.google.play.developer.reporting.VitalsErrorsServiceGrpcKt
import com.google.play.developer.reporting.fetchReleaseFilterOptionsRequest
import com.google.play.developer.reporting.getErrorCountMetricSetRequest
import com.google.play.developer.reporting.queryErrorCountMetricSetRequest
import com.google.play.developer.reporting.searchAccessibleAppsRequest
import com.google.play.developer.reporting.searchErrorIssuesRequest
import com.google.play.developer.reporting.searchErrorReportsRequest
import com.google.play.developer.reporting.timelineSpec
import com.google.type.TimeZone
import com.intellij.openapi.diagnostic.Logger
import io.grpc.Channel
import io.grpc.ClientInterceptor
import java.time.ZoneId

class VitalsGrpcClientImpl(channel: Channel, authTokenInterceptor: ClientInterceptor) :
  VitalsGrpcClient {

  private val vitalsReportingServiceGrpcClient =
    ReportingServiceGrpcKt.ReportingServiceCoroutineStub(channel)
      .withInterceptors(authTokenInterceptor)
  private val vitalsErrorGrpcClient =
    VitalsErrorsServiceGrpcKt.VitalsErrorsServiceCoroutineStub(channel)
      .withInterceptors(authTokenInterceptor)

  override suspend fun listAccessibleApps(maxNumResults: Int): List<AppConnection> {
    val searchAccessibleAppsRequest = searchAccessibleAppsRequest { pageSize = maxNumResults }

    return retryRpc {
        vitalsReportingServiceGrpcClient.searchAccessibleApps(searchAccessibleAppsRequest)
      }
      .appsList
      .map { AppConnection(it.name.substringAfter('/'), it.displayName) }
  }

  override suspend fun queryErrorCountMetrics(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId?,
    dimensions: List<DimensionType>,
    metrics: List<MetricType>,
    freshness: Freshness,
    maxNumResults: Int,
  ): List<DimensionsAndMetrics> {
    val timezone: TimeZone = freshness.latestEndTime.timeZone
    val zoneId = ZoneId.of(timezone.id)
    val queryErrorCountMetricsSetRequest = queryErrorCountMetricSetRequest {
      name = "${connection.clientId}/errorCountMetricSet" // Format: apps/{app}/errorCountMetricSet

      timelineSpec = timelineSpec {
        aggregationPeriod = freshness.timeGranularity.toProto()
        startTime =
          filters.interval.startTime.toProtoDateTime(zoneId).truncate(freshness.timeGranularity)
        endTime = freshness.latestEndTime.truncate(freshness.timeGranularity)
      }

      this.dimensions.addAll(dimensions.map { it.value })
      this.metrics.addAll(metrics.map { it.value })

      filter =
        FilterBuilder()
          .apply {
            addVersions(filters.versions)
            addReportTypes(filters.eventTypes)
            addDevices(filters.devices)
            addOperatingSystems(filters.operatingSystems)
            addVisibilityType(filters.visibilityType)
            issueId?.let { addIssue(issueId) }
          }
          .build()

      pageSize = maxNumResults
    }

    return retryRpc {
        vitalsErrorGrpcClient.queryErrorCountMetricSet(queryErrorCountMetricsSetRequest)
      }
      .rowsList
      .map { row ->
        DimensionsAndMetrics(
          dimensions = row.dimensionsList.map { Dimension.fromProto(it) },
          metrics = row.metricsList.map { Metric.fromProto(it) },
        )
      }
  }

  override suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection): List<Freshness> {
    val queryErrorCountMetricsSetRequest = getErrorCountMetricSetRequest {
      name = "${connection.clientId}/errorCountMetricSet" // Format: apps/{app}/errorCountMetricSet
    }

    return retryRpc {
        vitalsErrorGrpcClient.getErrorCountMetricSet(queryErrorCountMetricsSetRequest)
      }
      .freshnessInfo
      .freshnessesList
      .mapNotNull {
        val timeGranularity =
          when (it.aggregationPeriod) {
            AggregationPeriod.HOURLY -> TimeGranularity.HOURLY
            AggregationPeriod.DAILY -> TimeGranularity.DAILY
            AggregationPeriod.FULL_RANGE -> TimeGranularity.FULL_RANGE
            else -> {
              Logger.getInstance(VitalsGrpcClientImpl::class.java)
                .warn("${it.aggregationPeriod} is not recognized.")
              return@mapNotNull null
            }
          }

        Freshness(timeGranularity = timeGranularity, latestEndTime = it.latestEndTime)
      }
  }

  override suspend fun getReleases(connection: Connection): List<Version> {
    val fetchReleaseFilterOptionsRequest = fetchReleaseFilterOptionsRequest {
      name = connection.clientId
    }

    return retryRpc {
        vitalsReportingServiceGrpcClient.fetchReleaseFilterOptions(fetchReleaseFilterOptionsRequest)
      }
      .tracksList
      .extract()
  }

  override suspend fun listTopIssues(
    connection: Connection,
    filters: QueryFilters,
    maxNumResults: Int,
    pageTokenFromPreviousCall: String?,
  ): List<IssueDetails> {
    val searchErrorIssuesRequest = searchErrorIssuesRequest {
      parent = connection.clientId
      interval = filters.interval.toProtoDateTime(TimeGranularity.HOURLY)
      pageSize = maxNumResults
      sampleErrorReportLimit = 1
      filter =
        FilterBuilder()
          .apply {
            addVersions(filters.versions)
            addFailureTypes(filters.eventTypes)
            addVisibilityType(filters.visibilityType)
            addDevices(filters.devices)
            addOperatingSystems(filters.operatingSystems)
          }
          .build()
    }

    return retryRpc { vitalsErrorGrpcClient.searchErrorIssues(searchErrorIssuesRequest) }
      .errorIssuesList // It's sorted by error report count.
      .map { it.toIssueDetails() }
  }

  override suspend fun searchErrorReportByReportIds(
    connection: Connection,
    filters: QueryFilters,
    reportIds: List<String>,
    stackTraceGroupParser: StackTraceGroupParser,
  ): List<Event> {
    val errorReports = mutableListOf<Event>()
    val requestBase = searchErrorReportsRequest {
      parent = connection.clientId
      interval = filters.interval.toProtoDateTime(TimeGranularity.HOURLY)
      filter =
        FilterBuilder()
          .apply {
            addVersions(filters.versions)
            addVisibilityType(filters.visibilityType)
            addDevices(filters.devices)
            addOperatingSystems(filters.operatingSystems)
            addReportIds(reportIds)
          }
          .build()
    }

    var nextPageToken = ""
    do {
      val request = requestBase.toBuilder().apply { pageToken = nextPageToken }.build()
      val response = retryRpc { vitalsErrorGrpcClient.searchErrorReports(request) }
      errorReports.addAll(response.errorReportsList.map { it.toSampleEvent(stackTraceGroupParser) })
      nextPageToken = response.nextPageToken
    } while (nextPageToken.isNotEmpty())
    return errorReports
  }

  override suspend fun searchErrorReportByIssueId(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId,
    stackTraceGroupParser: StackTraceGroupParser,
  ): Event {
    val searchErrorReportsRequest = searchErrorReportsRequest {
      parent = connection.clientId
      interval = filters.interval.toProtoDateTime(TimeGranularity.HOURLY)
      filter =
        FilterBuilder()
          .apply {
            addErrorIssue(issueId)
            addVersions(filters.versions)
            addVisibilityType(filters.visibilityType)
            addDevices(filters.devices)
            addOperatingSystems(filters.operatingSystems)
          }
          .build()
      pageSize = 1
    }

    return retryRpc { vitalsErrorGrpcClient.searchErrorReports(searchErrorReportsRequest) }
      .errorReportsList
      .map { it.toSampleEvent(stackTraceGroupParser) }
      .firstOrNull() ?: Event.EMPTY
  }
}
