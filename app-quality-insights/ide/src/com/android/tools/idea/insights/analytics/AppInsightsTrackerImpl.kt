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

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.EventDetails
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.InsightSentiment.Sentiment
import com.intellij.openapi.project.Project

class AppInsightsTrackerImpl(
  private val project: Project,
  private val insightsProductType: AppInsightsTracker.ProductType,
) : AppInsightsTracker {

  override fun logZeroState(
    event: AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails
  ) {
    log(project.name) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.ZERO_STATE
      zeroStateDetails = event
    }
  }

  override fun logCrashesFetched(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails,
  ) {
    log(unanonymizedAppId) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.CRASHES_FETCHED
      fetchDetails = event
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logCrashListDetailView(
    event: AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails
  ) {
    log(project.name) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.CRASH_LIST_DETAILS_VIEW
      crashOpenDetails = event
    }
  }

  override fun logStacktraceClicked(
    mode: ConnectionMode?,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails,
  ) {
    log(project.name) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.STACKTRACE_CLICKED
      stacktraceDetails = event
      mode?.let { isOffline = it.isOfflineMode() }
    }
  }

  override fun logConsoleLinkClicked(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsConsoleLinkDetails,
  ) {
    log(project.name) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.FB_CONSOLE_LINK_CLICKED
      consoleLinkDetails = event
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logError(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsErrorDetails,
  ) {
    log(project.name) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.ERROR
      errorDetails = event
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logIssueStatusChanged(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails,
  ) {
    log(unanonymizedAppId) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.ISSUE_STATUS_CHANGED
      issueChangedDetails = event
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logNotesAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails,
  ) {
    log(unanonymizedAppId) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.NOTE
      notesDetails = event
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logOfflineTransitionAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails,
  ) {
    // If the transition is redundant, ie: from online to online, then skip tracking this metric.
    if (
      mode.isOfflineMode() &&
        event.ordinal ==
          AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails.ONLINE_TO_OFFLINE
            .ordinal ||
        !mode.isOfflineMode() &&
          event.ordinal ==
            AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails.OFFLINE_TO_ONLINE
              .ordinal
    ) {
      return
    }
    log(unanonymizedAppId) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.MODE_TRANSITION
      modeTransitionDetails = event
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logEventViewed(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    issueId: String,
    eventId: String,
    isFetched: Boolean,
  ) {
    log(unanonymizedAppId) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.EVENT_VIEWED
      eventDetails =
        EventDetails.newBuilder()
          .apply {
            this.issueId = AnonymizerUtil.anonymizeUtf8(issueId)
            this.eventId = AnonymizerUtil.anonymizeUtf8(eventId)
            this.isFetched = isFetched
          }
          .build()
      isOffline = mode.isOfflineMode()
    }
  }

  override fun logInsightSentiment(
    sentiment: Sentiment,
    crashType: AppQualityInsightsUsageEvent.CrashType,
    insight: AiInsight,
  ) {
    log(project.name) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.INSIGHT_SENTIMENT
      insightSentiment =
        AppQualityInsightsUsageEvent.InsightSentiment.newBuilder()
          .apply {
            this.sentiment = sentiment
            this.experiment = insight.experiment.toProto()
            this.crashType = crashType
            this.source = insight.insightSource.toProto()
          }
          .build()
    }
  }

  override fun logInsightFetch(
    unanonymizedAppId: String,
    crashType: FailureType,
    insight: AiInsight,
  ) {
    log(unanonymizedAppId) {
      type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.INSIGHT_FETCH
      insightFetchDetails =
        AppQualityInsightsUsageEvent.InsightFetchDetails.newBuilder()
          .apply {
            this.crashType = crashType.toCrashType()
            this.experiment = insight.experiment.toProto()
            this.isCached = insight.isCached
            this.source = insight.insightSource.toProto()
          }
          .build()
    }
  }

  private fun log(
    unanonymizedAppId: String,
    builder: AppQualityInsightsUsageEvent.Builder.() -> Unit,
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder()
            .apply {
              appId = AnonymizerUtil.anonymizeUtf8(unanonymizedAppId)
              productType = insightsProductType.toProtoProductType()
              builder()
            }
            .build()
        )
        .withProjectId(project)
    )
  }

  private fun generateAndroidStudioEventBuilder(): AndroidStudioEvent.Builder {
    return AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.FIREBASE_ASSISTANT)
      .setKind(AndroidStudioEvent.EventKind.APP_QUALITY_INSIGHTS_USAGE)
  }
}
