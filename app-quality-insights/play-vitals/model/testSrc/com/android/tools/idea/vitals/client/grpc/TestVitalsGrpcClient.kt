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
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.QueryFilters
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.DimensionsAndMetrics
import com.android.tools.idea.vitals.datamodel.Freshness
import com.android.tools.idea.vitals.datamodel.MetricType

open class TestVitalsGrpcClient : VitalsGrpcClient {
  override suspend fun listAccessibleApps(maxNumResults: Int): List<AppConnection> {
    return emptyList()
  }

  override suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection): List<Freshness> {
    return emptyList()
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
    return emptyList()
  }

  override suspend fun getReleases(connection: Connection): List<Version> {
    return emptyList()
  }

  override suspend fun searchErrorReports(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId,
    maxNumResults: Int,
  ): List<Event> {
    return emptyList()
  }

  override suspend fun listTopIssues(
    connection: Connection,
    filters: QueryFilters,
    maxNumResults: Int,
    pageTokenFromPreviousCall: String?,
  ): List<IssueDetails> {
    return emptyList()
  }
}
