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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.QueryFilters
import com.android.tools.idea.insights.client.retryRpc
import com.android.tools.idea.io.grpc.ClientInterceptor
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.netty.NettyChannelBuilder
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
import com.google.gct.login.GoogleLogin
import com.google.play.developer.reporting.AggregationPeriod
import com.google.play.developer.reporting.FetchReleaseFilterOptionsRequest
import com.google.play.developer.reporting.GetErrorCountMetricSetRequest
import com.google.play.developer.reporting.QueryErrorCountMetricSetRequest
import com.google.play.developer.reporting.ReportingServiceGrpc
import com.google.play.developer.reporting.SearchAccessibleAppsRequest
import com.google.play.developer.reporting.SearchErrorIssuesRequest
import com.google.play.developer.reporting.SearchErrorReportsRequest
import com.google.play.developer.reporting.TimeZone
import com.google.play.developer.reporting.TimelineSpec
import com.google.play.developer.reporting.VitalsErrorsServiceGrpc
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.guava.await

class VitalsGrpcClientImpl(channel: ManagedChannel, authTokenInterceptor: ClientInterceptor) :
  VitalsGrpcClient {

  private val vitalsReportingServiceGrpcClient =
    ReportingServiceGrpc.newFutureStub(channel).withInterceptors(authTokenInterceptor)
  private val vitalsErrorGrpcClient =
    VitalsErrorsServiceGrpc.newFutureStub(channel).withInterceptors(authTokenInterceptor)

  override suspend fun listAccessibleApps(maxNumResults: Int): List<AppConnection> {
    val searchAccessibleAppsRequest =
      SearchAccessibleAppsRequest.newBuilder().apply { pageSize = maxNumResults }.build()

    return retryRpc {
        vitalsReportingServiceGrpcClient.searchAccessibleApps(searchAccessibleAppsRequest).await()
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
    maxNumResults: Int
  ): List<DimensionsAndMetrics> {
    val timezone: TimeZone = freshness.latestEndTime.timeZone
    val zoneId = ZoneId.of(timezone.id)

    val timelineSpecBuilder =
      TimelineSpec.newBuilder().apply {
        aggregationPeriod = freshness.timeGranularity.toProto()
        startTime =
          filters.interval.startTime.toProtoDateTime(zoneId).truncate(freshness.timeGranularity)
        endTime = freshness.latestEndTime.truncate(freshness.timeGranularity)
      }

    val queryErrorCountMetricsSetRequest =
      QueryErrorCountMetricSetRequest.newBuilder()
        .apply {
          name =
            "${connection.clientId}/errorCountMetricSet" // Format: apps/{app}/errorCountMetricSet
          timelineSpec = timelineSpecBuilder.build()

          addAllDimensions(dimensions.map { it.value })
          addAllMetrics(metrics.map { it.value })

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
        .build()

    return retryRpc {
        vitalsErrorGrpcClient.queryErrorCountMetricSet(queryErrorCountMetricsSetRequest).await()
      }
      .rowsList
      .map { row ->
        DimensionsAndMetrics(
          dimensions = row.dimensionsList.map { Dimension.fromProto(it) },
          metrics = row.metricsList.map { Metric.fromProto(it) }
        )
      }
  }

  override suspend fun getErrorCountMetricsFreshnessInfo(
    connection: Connection,
  ): List<Freshness> {
    val queryErrorCountMetricsSetRequest =
      GetErrorCountMetricSetRequest.newBuilder()
        .apply {
          name =
            "${connection.clientId}/errorCountMetricSet" // Format: apps/{app}/errorCountMetricSet
        }
        .build()

    return retryRpc {
        vitalsErrorGrpcClient.getErrorCountMetricSet(queryErrorCountMetricsSetRequest).await()
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
              LOG.warn("${it.aggregationPeriod} is not recognized.")
              return@mapNotNull null
            }
          }

        Freshness(timeGranularity = timeGranularity, latestEndTime = it.latestEndTime)
      }
  }

  override suspend fun getReleases(connection: Connection): List<Version> {
    val fetchReleaseFilterOptionsRequest =
      FetchReleaseFilterOptionsRequest.newBuilder().apply { name = connection.clientId }.build()

    return retryRpc {
        vitalsReportingServiceGrpcClient
          .fetchReleaseFilterOptions(fetchReleaseFilterOptionsRequest)
          .await()
      }
      .tracksList
      .extract()
  }

  override suspend fun listTopIssues(
    connection: Connection,
    filters: QueryFilters,
    maxNumResults: Int,
    pageTokenFromPreviousCall: String?
  ): List<IssueDetails> {
    val searchErrorIssuesRequest =
      SearchErrorIssuesRequest.newBuilder()
        .apply {
          parent = connection.clientId
          interval = filters.interval.toProtoDateTime(TimeGranularity.HOURLY)
          pageSize = maxNumResults
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
        .build()

    return retryRpc { vitalsErrorGrpcClient.searchErrorIssues(searchErrorIssuesRequest).await() }
      .errorIssuesList // It's sorted by error report count.
      .map { it.toIssueDetails() }
  }

  override suspend fun searchErrorReports(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId,
    maxNumResults: Int
  ): List<Event> {
    val searchErrorReportsRequest =
      SearchErrorReportsRequest.newBuilder()
        .apply {
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
          pageSize = maxNumResults
        }
        .build()

    return retryRpc { vitalsErrorGrpcClient.searchErrorReports(searchErrorReportsRequest).await() }
      .errorReportsList
      .map { it.toSampleEvent() }
  }

  companion object {
    private val LOG = Logger.getInstance(VitalsGrpcClientImpl::class.java)

    fun create(parentDisposable: Disposable): VitalsGrpcClientImpl {
      application.assertIsNonDispatchThread()
      val address = StudioFlags.PLAY_VITALS_GRPC_SERVER.get()
      LOG.info("Play Vitals gRpc server connected at $address")
      return VitalsGrpcClientImpl(
        channel =
          NettyChannelBuilder.forTarget(address)
            .apply {
              if (StudioFlags.PLAY_VITALS_GRPC_USE_TRANSPORT_SECURITY.get()) useTransportSecurity()
              else usePlaintext()
            }
            .build()
            .also {
              Disposer.register(parentDisposable) {
                it.shutdown()
                it.awaitTermination(1, TimeUnit.SECONDS)
              }
            },
        authTokenInterceptor = GoogleLogin.instance.getActiveUserAuthInterceptor()
      )
    }
  }
}
