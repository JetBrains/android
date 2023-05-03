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

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.FAKE_50_DAYS_AGO
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.FakeTimeProvider
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.Interval
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.client.QueryFilters
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.client.grpc.TestVitalsGrpcClient
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.DimensionsAndMetrics
import com.android.tools.idea.vitals.datamodel.Freshness
import com.android.tools.idea.vitals.datamodel.MetricType
import com.android.tools.idea.vitals.datamodel.TimeGranularity
import com.google.common.truth.Truth.assertThat
import com.google.play.developer.reporting.DateTime
import com.intellij.testFramework.DisposableRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VitalsClientTest {

  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun checkAggregationUtils() {
    val list =
      listOf(
        Pair("a", 1L),
        Pair("b", 0L),
        Pair("c", 2L),
        Pair("a", 1L),
        Pair("b", 0L),
        Pair("c", 100L),
        Pair("d", 0L)
      )

    val aggregated = list.aggregateToWithCount()
    assertThat(aggregated)
      .containsExactlyElementsIn(
        listOf(WithCount(2L, "a"), WithCount(0L, "b"), WithCount(102L, "c"), WithCount(0L, "d"))
      )
  }

  @Test
  fun `client returns top cached issues when offline`() = runTest {
    val cache = AppInsightsCacheImpl()
    val grpcClient = TestVitalsGrpcClient() // return empty result for every API call.
    val client = VitalsClient(disposableRule.disposable, cache, grpcClient)

    cache.populateIssues(TEST_CONNECTION_1, listOf(ISSUE1))

    assertThat(
        client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
              signal = SignalType.SIGNAL_UNSPECIFIED
            )
          ),
          null,
          ConnectionMode.OFFLINE
        )
      )
      .isEqualTo(
        LoadingState.Ready(
          IssueResponse(
            listOf(
              ISSUE1.copy(
                issueDetails = ISSUE1.issueDetails.copy(impactedDevicesCount = 0L, eventsCount = 0L)
              )
            ),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.FULL
          )
        )
      )
  }

  @Test
  fun `client caches events for use in the future`() = runTest {
    val cache = AppInsightsCacheImpl()
    val grpcClient =
      object : TestVitalsGrpcClient() {
        override suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection) =
          listOf(Freshness(TimeGranularity.FULL_RANGE, DateTime.getDefaultInstance()))

        override suspend fun queryErrorCountMetrics(
          connection: Connection,
          filters: QueryFilters,
          issueId: IssueId?,
          dimensions: List<DimensionType>,
          metrics: List<MetricType>,
          freshness: Freshness,
          maxNumResults: Int
        ) = emptyList<DimensionsAndMetrics>()

        override suspend fun searchErrorReports(
          connection: Connection,
          filters: QueryFilters,
          issueId: IssueId,
          maxNumResults: Int
        ) = listOf(ISSUE1.sampleEvent)

        override suspend fun listTopIssues(
          connection: Connection,
          filters: QueryFilters,
          maxNumResults: Int,
          pageTokenFromPreviousCall: String?
        ): List<IssueDetails> = listOf(ISSUE1.issueDetails)
      }
    val client = VitalsClient(disposableRule.disposable, cache, grpcClient)

    val responseIssue =
      (client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
            )
          ),
          null,
          ConnectionMode.ONLINE
        ) as LoadingState.Ready)
        .value
        .issues
        .single()

    assertThat(responseIssue).isEqualTo(ISSUE1)

    val offlineResponse =
      (client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
            )
          ),
          null,
          ConnectionMode.OFFLINE
        ) as LoadingState.Ready)
        .value
        .issues
        .single()

    assertThat(offlineResponse)
      .isEqualTo(
        ISSUE1.copy(
          issueDetails = ISSUE1.issueDetails.copy(impactedDevicesCount = 0L, eventsCount = 0L)
        )
      )
  }
}
