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

import com.android.flags.junit.FlagRule
import com.android.testutils.time.FakeClock
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.Interval
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.TEST_ISSUE1
import com.android.tools.idea.vitals.client.grpc.DISTINCT_USERS
import com.android.tools.idea.vitals.client.grpc.ERROR_REPORT_COUNT
import com.android.tools.idea.vitals.client.grpc.VitalsGrpcClientImpl
import com.android.tools.idea.vitals.client.grpc.VitalsGrpcConnectionRule
import com.android.tools.idea.vitals.client.grpc.createIssueRequest
import com.android.tools.idea.vitals.client.grpc.toProtoDateTime
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.TimeGranularity
import com.google.common.truth.Truth.assertThat
import com.google.play.developer.reporting.AggregationPeriod
import com.google.play.developer.reporting.FetchReleaseFilterOptionsRequest
import com.google.play.developer.reporting.GetErrorCountMetricSetRequest
import com.google.play.developer.reporting.QueryErrorCountMetricSetRequest
import com.google.play.developer.reporting.SearchErrorIssuesRequest
import com.google.play.developer.reporting.SearchErrorReportsRequest
import com.google.play.developer.reporting.TimelineSpec
import com.intellij.testFramework.DisposableRule
import com.studiogrpc.testutils.ForwardingInterceptor
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class VitalsClientRequestProtoTest {

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val grpcRule = VitalsGrpcConnectionRule(TEST_CONNECTION_1)

  @get:Rule val flagRule = FlagRule(StudioFlags.CRASHLYTICS_J_UI, true)

  private val clock = FakeClock()

  init {
    grpcRule.database.addIssue(TEST_ISSUE1)
  }

  @Test
  fun `client sends correct proto in request to listTopOpenIssues`() =
    runBlocking<Unit> {
      val client =
        VitalsClient(
          disposableRule.disposable,
          AppInsightsCacheImpl(),
          ForwardingInterceptor,
          VitalsGrpcClientImpl(grpcRule.grpcChannel, ForwardingInterceptor),
        )

      val request = createIssueRequest(TEST_CONNECTION_1, clock)
      client.listTopOpenIssues(request)

      val events = grpcRule.collectEvents()

      assertThat(events).hasSize(9)

      val errorIssueRequest = events.filterIsInstance<SearchErrorIssuesRequest>().single()
      assertThat(errorIssueRequest.parent).isEqualTo(TEST_CONNECTION_1.clientId)
      assertThat(errorIssueRequest.interval)
        .isEqualTo(request.filters.interval.toProtoDateTime(TimeGranularity.HOURLY))
      assertThat(errorIssueRequest.filter)
        .isEqualTo("(errorIssueType = ANR OR errorIssueType = CRASH)")

      val errorReportRequest = events.filterIsInstance<SearchErrorReportsRequest>().single()
      assertThat(errorReportRequest.interval)
        .isEqualTo(request.filters.interval.toProtoDateTime(TimeGranularity.HOURLY))
      assertThat(errorReportRequest.filter).isEqualTo("(errorIssueId = ${TEST_ISSUE1.id.value})")

      val releaseFilteringRequest =
        events.filterIsInstance<FetchReleaseFilterOptionsRequest>().single()
      assertThat(releaseFilteringRequest.name).isEqualTo(TEST_CONNECTION_1.clientId)

      val getErrorCountMetricSetRequests = events.filterIsInstance<GetErrorCountMetricSetRequest>()
      assertThat(getErrorCountMetricSetRequests).hasSize(3)
      assertThat(getErrorCountMetricSetRequests.toSet())
        .containsExactly(
          GetErrorCountMetricSetRequest.newBuilder()
            .apply { name = "apps/${TEST_CONNECTION_1.appId}/errorCountMetricSet" }
            .build()
        )

      val queryErrorCountMetricSetRequests =
        events.filterIsInstance<QueryErrorCountMetricSetRequest>()
      assertThat(queryErrorCountMetricSetRequests).hasSize(3)
      assertQueryErrorCountMetricSetRequest(
        queryErrorCountMetricSetRequests.first {
          it.dimensionsList.contains(DimensionType.API_LEVEL.value)
        },
        request.filters.interval,
        ERROR_REPORT_COUNT,
      )
      assertQueryErrorCountMetricSetRequest(
        queryErrorCountMetricSetRequests.first {
          it.dimensionsList.contains(DimensionType.DEVICE_MODEL.value)
        },
        request.filters.interval,
        ERROR_REPORT_COUNT,
      )
      assertQueryErrorCountMetricSetRequest(
        queryErrorCountMetricSetRequests.first {
          it.dimensionsList.contains(DimensionType.VERSION_CODE.value)
        },
        request.filters.interval,
        ERROR_REPORT_COUNT,
      )
    }

  @Test
  fun `clients send correct proto in request to get distribution`() =
    runBlocking<Unit> {
      val client =
        VitalsClient(
          disposableRule.disposable,
          AppInsightsCacheImpl(),
          ForwardingInterceptor,
          VitalsGrpcClientImpl(grpcRule.grpcChannel, ForwardingInterceptor),
        )

      val request = createIssueRequest(TEST_CONNECTION_1, clock)
      client.getIssueDetails(TEST_ISSUE1.id, request)

      val events = grpcRule.collectEvents()

      assertThat(events).hasSize(4)

      val getRequests = events.filterIsInstance<GetErrorCountMetricSetRequest>()
      assertThat(getRequests).hasSize(2)
      assertThat(getRequests.toSet())
        .containsExactly(
          GetErrorCountMetricSetRequest.newBuilder()
            .apply { name = "apps/${TEST_CONNECTION_1.appId}/errorCountMetricSet" }
            .build()
        )

      val queryRequests = events.filterIsInstance<QueryErrorCountMetricSetRequest>()
      assertThat(queryRequests).hasSize(2)
      assertQueryErrorCountMetricSetRequest(
        queryRequests.first { it.dimensionsList.contains(DimensionType.DEVICE_MODEL.value) },
        request.filters.interval,
        DISTINCT_USERS,
        TEST_ISSUE1.id.value,
      )
      assertQueryErrorCountMetricSetRequest(
        queryRequests.first { it.dimensionsList.contains(DimensionType.API_LEVEL.value) },
        request.filters.interval,
        DISTINCT_USERS,
        TEST_ISSUE1.id.value,
      )
    }

  private fun assertQueryErrorCountMetricSetRequest(
    request: QueryErrorCountMetricSetRequest,
    interval: Interval,
    expectedMetric: String,
    issueId: String? = null,
  ) {
    assertThat(request.name).isEqualTo("apps/${TEST_CONNECTION_1.appId}/errorCountMetricSet")
    assertThat(request.timelineSpec)
      .isEqualTo(
        TimelineSpec.newBuilder()
          .apply {
            aggregationPeriod = AggregationPeriod.FULL_RANGE
            startTime = interval.startTime.toProtoDateTime(ZoneId.of("UTC"))
            endTime = interval.endTime.toProtoDateTime(ZoneId.of("UTC"))
          }
          .build()
      )
    if (request.dimensionsList.contains(DimensionType.API_LEVEL.value)) {
      assertThat(request.dimensionsList)
        .containsExactly(DimensionType.REPORT_TYPE.value, DimensionType.API_LEVEL.value)
    } else if (request.dimensionsList.contains(DimensionType.DEVICE_MODEL.value)) {
      assertThat(request.dimensionsList)
        .containsExactly(
          DimensionType.REPORT_TYPE.value,
          DimensionType.DEVICE_BRAND.value,
          DimensionType.DEVICE_MODEL.value,
          DimensionType.DEVICE_TYPE.value,
        )
    } else if (request.dimensionsList.contains(DimensionType.VERSION_CODE.value)) {
      assertThat(request.dimensionsList)
        .containsExactly(DimensionType.REPORT_TYPE.value, DimensionType.VERSION_CODE.value)
    } else {
      throw AssertionError("Unexpected dimensions in: ${request.dimensionsList}")
    }
    assertThat(request.metricsList).containsExactly(expectedMetric)
    assertThat(request.filter)
      .isEqualTo(
        "${issueId?.let { "(issueId = ${issueId}) AND " } ?: "" }(reportType = ANR OR reportType = CRASH)"
      )
  }
}
