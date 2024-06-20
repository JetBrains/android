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
package com.android.tools.idea.insights.analytics

import com.android.tools.idea.insights.ConnectionMode
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

interface AppInsightsTracker {
  fun logZeroState(event: AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails)

  fun logCrashesFetched(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails,
  )

  fun logCrashListDetailView(event: AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails)

  fun logStacktraceClicked(
    mode: ConnectionMode?,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails,
  )

  fun logConsoleLinkClicked(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsConsoleLinkDetails,
  )

  fun logError(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsErrorDetails,
  )

  fun logIssueStatusChanged(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails,
  )

  fun logNotesAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails,
  )

  fun logOfflineTransitionAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails,
  )

  enum class ProductType {
    CRASHLYTICS,
    PLAY_VITALS;

    fun toProtoProductType(): AppQualityInsightsUsageEvent.AppQualityInsightsProductType =
      when (this) {
        CRASHLYTICS -> AppQualityInsightsUsageEvent.AppQualityInsightsProductType.CRASHLYTICS
        PLAY_VITALS -> AppQualityInsightsUsageEvent.AppQualityInsightsProductType.PLAY_VITALS
      }
  }
}
