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

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.toIssueRequest
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.createVitalsFilters
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import java.time.Clock

const val ERROR_REPORT_COUNT = "errorReportCount"
const val CRASH = "CRASH"
const val DISTINCT_USERS = "distinctUsers"

fun createIssueRequest(connection: VitalsConnection = TEST_CONNECTION_1, clock: Clock) =
  AppInsightsState(
      Selection(connection, listOf(connection)),
      createVitalsFilters(),
      LoadingState.Loading,
      LoadingState.Ready(null),
      LoadingState.Ready(null),
      LoadingState.Ready(null),
      LoadingState.Ready(null),
      Permission.NONE,
      ConnectionMode.ONLINE
    )
    .toIssueRequest(clock)!!
