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

/** TODO: maybe move the below to a common place. */
private const val DEFAULT_MAX_APPS_PER_CALL = 1000
private const val DEFAULT_MAX_DATA_POINTS_PER_CALL = 10000
private const val DEFAULT_MAX_OPEN_ISSUES_PER_CALL = 50

interface VitalsGrpcClient {
  /** Returns a list of [Connection]s (App IDs) that are accessible. */
  suspend fun listAccessibleApps(
    maxNumResults: Int = DEFAULT_MAX_APPS_PER_CALL
  ): List<AppConnection>

  /**
   * Returns freshness info (query period granularity & valid end-time) which is required for the
   * following metrics query ([queryErrorCountMetrics]).
   */
  suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection): List<Freshness>

  /**
   * Returns metrics based on the passed-in searching criteria.
   *
   * Metrics we get here are quite raw; E.g. they could be hourly-based or daily-based data or so.
   *
   * Please note we need to call [getErrorCountMetricsFreshnessInfo] first to get valid [freshness]
   * information.
   */
  suspend fun queryErrorCountMetrics(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId?,
    dimensions: List<DimensionType>,
    metrics: List<MetricType>,
    freshness: Freshness,
    maxNumResults: Int = DEFAULT_MAX_DATA_POINTS_PER_CALL,
  ): List<DimensionsAndMetrics>

  /** Returns versions that are part of the releases. */
  suspend fun getReleases(connection: Connection): List<Version>

  /**
   * Returns error reports (sample events) based on the passed-in issue id and other general
   * searching criteria.
   */
  suspend fun searchErrorReports(
    connection: Connection,
    filters: QueryFilters,
    issueId: IssueId,
    maxNumResults: Int,
  ): List<Event>

  /** Returns top issues based on the passed-in searching criteria. */
  suspend fun listTopIssues(
    connection: Connection,
    filters: QueryFilters,
    maxNumResults: Int = DEFAULT_MAX_OPEN_ISSUES_PER_CALL,
    pageTokenFromPreviousCall: String? = null,
  ): List<IssueDetails>
}
