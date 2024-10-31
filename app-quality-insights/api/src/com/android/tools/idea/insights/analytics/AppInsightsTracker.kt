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
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.experiments.Experiment
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.CrashType
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.InsightExperiment
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.InsightSentiment.Sentiment

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

  fun logEventViewed(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    issueId: String,
    eventId: String,
  )

  fun logEventsFetched(
    unanonymizedAppId: String,
    issueId: String,
    crashType: FailureType,
    isFirstFetch: Boolean,
  )

  fun logInsightSentiment(sentiment: Sentiment, crashType: CrashType, insight: AiInsight)

  fun logInsightFetch(
    unanonymizedAppId: String,
    crashType: FailureType,
    insight: AiInsight,
    contextLimit: Int,
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

fun Experiment.toProto() =
  when (this) {
    Experiment.UNKNOWN -> InsightExperiment.UNKNOWN_EXPERIMENT
    Experiment.CONTROL -> InsightExperiment.CONTROL
    Experiment.TOP_SOURCE -> InsightExperiment.TOP_SOURCE
    Experiment.TOP_THREE_SOURCES -> InsightExperiment.TOP_THREE_SOURCES
    Experiment.ALL_SOURCES -> InsightExperiment.ALL_SOURCES
  }
