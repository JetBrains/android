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
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.VisualLintEvent
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.jetbrains.android.facet.AndroidFacet

interface VisualLintUsageTracker {
  fun trackIssueCreation(
    issueType: VisualLintErrorType,
    origin: VisualLintOrigin,
    facet: AndroidFacet
  ) {
    track(VisualLintEvent.IssueEvent.CREATE_ISSUE, issueType, facet, origin)
  }

  fun trackIssueIgnored(
    issueType: VisualLintErrorType,
    origin: VisualLintOrigin,
    facet: AndroidFacet
  ) {
    track(VisualLintEvent.IssueEvent.IGNORE_ISSUE, issueType, facet, origin)
  }

  fun trackBackgroundRuleStatusChanged(issueType: VisualLintErrorType, enabled: Boolean) {
    val event =
      if (enabled) VisualLintEvent.IssueEvent.ENABLE_BACKGROUND_RULE
      else VisualLintEvent.IssueEvent.DISABLE_BACKGROUND_RULE
    track(event, issueType, null)
  }

  fun trackRuleStatusChanged(issueType: VisualLintErrorType, enabled: Boolean) {
    val event =
      if (enabled) VisualLintEvent.IssueEvent.ENABLE_RULE
      else VisualLintEvent.IssueEvent.DISABLE_RULE
    track(event, issueType, null)
  }

  fun trackCancelledBackgroundAnalysis() {
    track(VisualLintEvent.IssueEvent.CANCEL_BACKGROUND_ANALYSIS, null, null)
  }

  fun trackClickHyperLink(issueType: VisualLintErrorType, origin: VisualLintOrigin) {
    track(VisualLintEvent.IssueEvent.CLICK_DOCUMENTATION_LINK, issueType, null, origin)
  }

  fun trackFirstRunTime(timeMs: Long, facet: AndroidFacet?) {
    logEvent(facet) { VisualLintEvent.newBuilder().setUiCheckStartTimeMs(timeMs).build() }
  }

  fun trackVisiblePreviews(count: Int, facet: AndroidFacet?) {
    logEvent(facet) { VisualLintEvent.newBuilder().setVisiblePreviewsNumber(count).build() }
  }

  fun track(
    issueEvent: VisualLintEvent.IssueEvent,
    issueType: VisualLintErrorType?,
    facet: AndroidFacet?,
    origin: VisualLintOrigin? = null,
  ) {
    val metricsIssueType =
      when (issueType) {
        VisualLintErrorType.BOUNDS -> VisualLintEvent.IssueType.BOUNDS
        VisualLintErrorType.BOTTOM_NAV -> VisualLintEvent.IssueType.BOTTOM_NAV
        VisualLintErrorType.BOTTOM_APP_BAR -> VisualLintEvent.IssueType.BOTTOM_APP_BAR
        VisualLintErrorType.OVERLAP -> VisualLintEvent.IssueType.OVERLAP
        VisualLintErrorType.LONG_TEXT -> VisualLintEvent.IssueType.LONG_TEXT
        VisualLintErrorType.ATF -> VisualLintEvent.IssueType.ATF
        VisualLintErrorType.ATF_COLORBLIND -> VisualLintEvent.IssueType.ATF_COLORBLIND
        VisualLintErrorType.LOCALE_TEXT -> VisualLintEvent.IssueType.LOCALE_TEXT
        VisualLintErrorType.TEXT_FIELD_SIZE -> VisualLintEvent.IssueType.TEXT_FIELD_SIZE
        VisualLintErrorType.BUTTON_SIZE -> VisualLintEvent.IssueType.BUTTON_SIZE
        VisualLintErrorType.WEAR_MARGIN -> VisualLintEvent.IssueType.WEAR_MARGIN
        else -> VisualLintEvent.IssueType.UNKNOWN_TYPE
      }
    logEvent(facet) {
      VisualLintEvent.newBuilder()
        .setIssueType(metricsIssueType)
        .setIssueEvent(issueEvent)
        .setEventOrigin(
          when (origin) {
            VisualLintOrigin.UI_CHECK -> VisualLintEvent.EventOrigin.UI_CHECK
            VisualLintOrigin.XML_LINTING -> VisualLintEvent.EventOrigin.XML_LINTING
            null -> VisualLintEvent.EventOrigin.UNKNOWN_ORIGIN
          }
        )
        .build()
    }
  }

  fun logEvent(facet: AndroidFacet?, visualLintEventProvider: () -> VisualLintEvent)

  companion object {
    fun getInstance(): VisualLintUsageTracker {
      return if (AnalyticsSettings.optedIn) VisualLintUsageTrackerImpl
      else VisualLintNoOpUsageTracker
    }
  }
}

private object VisualLintUsageTrackerImpl : VisualLintUsageTracker {
  private val executorService =
    ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue(10))

  override fun logEvent(facet: AndroidFacet?, visualLintEventProvider: () -> VisualLintEvent) {
    try {
      executorService.execute {
        val layoutEditorEventBuilder =
          LayoutEditorEvent.newBuilder()
            .setType(LayoutEditorEvent.LayoutEditorEventType.VISUAL_LINT)
            .setVisualLintEvent(visualLintEventProvider())
        val studioEvent =
          AndroidStudioEvent.newBuilder()
            .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
            .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
            .setLayoutEditorEvent(layoutEditorEventBuilder.build())

        facet?.let { studioEvent.setApplicationId(it) }
        UsageTracker.log(studioEvent)
      }
    } catch (ignore: RejectedExecutionException) {}
  }
}

private object VisualLintNoOpUsageTracker : VisualLintUsageTracker {
  override fun logEvent(facet: AndroidFacet?, visualLintEventProvider: () -> VisualLintEvent) = Unit
}

enum class VisualLintOrigin {
  XML_LINTING,
  UI_CHECK
}
