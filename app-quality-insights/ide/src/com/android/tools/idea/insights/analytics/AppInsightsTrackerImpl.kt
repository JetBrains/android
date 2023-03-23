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
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.openapi.project.Project
import java.security.MessageDigest
import java.util.Random

class AppInsightsTrackerImpl(private val project: Project) : AppInsightsTracker {
  private val appIdSalt: Int = Random().nextInt()

  override fun logZeroState(
    event: AppQualityInsightsUsageEvent.AppQualityInsightsZeroStateDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(project.name)
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.ZERO_STATE
            zeroStateDetails = event
          }
        )
        .withProjectId(project)
    )
  }

  override fun logCrashesFetched(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(unanonymizedAppId)
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.CRASHES_FETCHED
            fetchDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  override fun logCrashListDetailView(
    event: AppQualityInsightsUsageEvent.AppQualityInsightsCrashOpenDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(project.name)
            type =
              AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.CRASH_LIST_DETAILS_VIEW
            crashOpenDetails = event
          }
        )
        .withProjectId(project)
    )
  }

  override fun logStacktraceClicked(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(project.name)
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.STACKTRACE_CLICKED
            stacktraceDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  override fun logConsoleLinkClicked(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsConsoleLinkDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(project.name)
            type =
              AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.FB_CONSOLE_LINK_CLICKED
            consoleLinkDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  override fun logMatchers(event: AppQualityInsightsUsageEvent.AppQualityInsightsMatcherDetails) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(project.name)
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.MATCHERS_INITIATED
            matcherDetails = event
          }
        )
        .withProjectId(project)
    )
  }

  override fun logError(
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsErrorDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(project.name)
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.ERROR
            errorDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  override fun logIssueStatusChanged(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsIssueChangedDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(unanonymizedAppId)
            type =
              AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.ISSUE_STATUS_CHANGED
            issueChangedDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  override fun logNotesAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsNotesDetails
  ) {
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = anonymizeAppId(unanonymizedAppId)
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.NOTE
            notesDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  override fun logOfflineTransitionAction(
    unanonymizedAppId: String,
    mode: ConnectionMode,
    event: AppQualityInsightsUsageEvent.AppQualityInsightsModeTransitionDetails
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
    UsageTracker.log(
      generateAndroidStudioEventBuilder()
        .setAppQualityInsightsUsageEvent(
          AppQualityInsightsUsageEvent.newBuilder().apply {
            appId = unanonymizedAppId?.let { anonymizeAppId(it) }
            type = AppQualityInsightsUsageEvent.AppQualityInsightsUsageEventType.MODE_TRANSITION
            modeTransitionDetails = event
            isOffline = mode.isOfflineMode()
          }
        )
        .withProjectId(project)
    )
  }

  private fun generateAndroidStudioEventBuilder(): AndroidStudioEvent.Builder {
    return AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.FIREBASE_ASSISTANT)
      .setKind(AndroidStudioEvent.EventKind.APP_QUALITY_INSIGHTS_USAGE)
  }

  private fun anonymizeAppId(appId: String?): String? {
    if (appId == null) return null
    val bytes = (appId + appIdSalt).toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
  }
}
