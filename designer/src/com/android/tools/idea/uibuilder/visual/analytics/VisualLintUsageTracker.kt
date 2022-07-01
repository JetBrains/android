/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.analytics

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAtfIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.VisualLintEvent
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

interface VisualLintUsageTracker {
  fun trackIssueCreation(issueType: VisualLintErrorType, facet: AndroidFacet) {
    track(VisualLintEvent.IssueEvent.CREATE_ISSUE, issueType, facet)
  }

  fun trackIssueExpanded(issue: Issue, facet: AndroidFacet) {
    val issueType = when (issue) {
      is VisualLintAtfIssue -> VisualLintErrorType.ATF
      is VisualLintRenderIssue -> issue.type
      else -> null
    }
    if (issueType != null) {
      track(VisualLintEvent.IssueEvent.EXPAND_ISSUE, issueType, facet)
    }
  }

  fun trackIssueIgnored(issueType: VisualLintErrorType, facet: AndroidFacet) {
    track(VisualLintEvent.IssueEvent.IGNORE_ISSUE, issueType, facet)
  }

  fun trackBackgroundRuleStatusChanged(issueType: VisualLintErrorType, enabled: Boolean) {
    val event = if (enabled) VisualLintEvent.IssueEvent.ENABLE_BACKGROUND_RULE else VisualLintEvent.IssueEvent.DISABLE_BACKGROUND_RULE
    track(event, issueType, null)
  }

  fun trackRuleStatusChanged(issueType: VisualLintErrorType, enabled: Boolean) {
    val event = if (enabled) VisualLintEvent.IssueEvent.ENABLE_RULE else VisualLintEvent.IssueEvent.DISABLE_RULE
    track(event, issueType, null)
  }

  fun trackCancelledBackgroundAnalysis() {
    track(VisualLintEvent.IssueEvent.CANCEL_BACKGROUND_ANALYSIS, null, null)
  }

  fun trackClickHyperLink(issueType: VisualLintErrorType) {
    track(VisualLintEvent.IssueEvent.CLICK_DOCUMENTATION_LINK, issueType, null)
  }

  fun track(issueEvent: VisualLintEvent.IssueEvent, issueType: VisualLintErrorType?, facet: AndroidFacet?)

  companion object {
    fun getInstance(): VisualLintUsageTracker {
      return if (AnalyticsSettings.optedIn)
        VisualLintUsageTrackerImpl
      else
        VisualLintNoOpUsageTracker
    }
  }
}

private object VisualLintUsageTrackerImpl : VisualLintUsageTracker {
  private val executorService = ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue(10))

  override fun track(issueEvent: VisualLintEvent.IssueEvent, issueType: VisualLintErrorType?, facet: AndroidFacet?) {
    val metricsIssueType = when (issueType) {
      VisualLintErrorType.BOUNDS -> VisualLintEvent.IssueType.BOUNDS
      VisualLintErrorType.BOTTOM_NAV -> VisualLintEvent.IssueType.BOTTOM_NAV
      VisualLintErrorType.BOTTOM_APP_BAR -> VisualLintEvent.IssueType.BOTTOM_APP_BAR
      VisualLintErrorType.OVERLAP -> VisualLintEvent.IssueType.OVERLAP
      VisualLintErrorType.LONG_TEXT -> VisualLintEvent.IssueType.LONG_TEXT
      VisualLintErrorType.ATF -> VisualLintEvent.IssueType.ATF
      VisualLintErrorType.LOCALE_TEXT -> VisualLintEvent.IssueType.LOCALE_TEXT
      VisualLintErrorType.TEXT_FIELD_SIZE -> VisualLintEvent.IssueType.TEXT_FIELD_SIZE
      VisualLintErrorType.BUTTON_SIZE -> VisualLintEvent.IssueType.BUTTON_SIZE
      VisualLintErrorType.WEAR_MARGIN -> VisualLintEvent.IssueType.WEAR_MARGIN
      else ->  VisualLintEvent.IssueType.UNKNOWN_TYPE
    }
    try {
      executorService.execute {
        val visualLintEventBuilder = VisualLintEvent.newBuilder()
          .setIssueType(metricsIssueType)
          .setIssueEvent(issueEvent)
        val layoutEditorEventBuilder = LayoutEditorEvent.newBuilder()
          .setType(LayoutEditorEvent.LayoutEditorEventType.VISUAL_LINT)
          .setVisualLintEvent(visualLintEventBuilder.build())
        val studioEvent = AndroidStudioEvent.newBuilder()
          .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
          .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
          .setLayoutEditorEvent(layoutEditorEventBuilder.build())

        facet?.let { studioEvent.setApplicationId(it) }
        UsageTracker.log(studioEvent)
      }
    }
    catch (ignore: RejectedExecutionException) { }
  }
}

private object VisualLintNoOpUsageTracker : VisualLintUsageTracker {
  override fun track(issueEvent: VisualLintEvent.IssueEvent, issueType: VisualLintErrorType?, facet: AndroidFacet?) {}
}