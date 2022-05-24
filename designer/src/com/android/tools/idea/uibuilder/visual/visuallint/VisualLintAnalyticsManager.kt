/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.VisualLintEvent

/**
 * Handles analytics for events related to Visual Lint
 */
class VisualLintAnalyticsManager(val surface: DesignSurface) {
  private fun track(issueType: VisualLintErrorType, issueEvent: VisualLintEvent.IssueEvent) {
    val metricsIssueType = when (issueType) {
      VisualLintErrorType.BOUNDS -> VisualLintEvent.IssueType.BOUNDS
      VisualLintErrorType.BOTTOM_NAV -> VisualLintEvent.IssueType.BOTTOM_NAV
      VisualLintErrorType.BOTTOM_APP_BAR -> VisualLintEvent.IssueType.BOTTOM_APP_BAR
      VisualLintErrorType.OVERLAP -> VisualLintEvent.IssueType.OVERLAP
      VisualLintErrorType.LONG_TEXT -> VisualLintEvent.IssueType.LONG_TEXT
      VisualLintErrorType.ATF -> VisualLintEvent.IssueType.ATF
      VisualLintErrorType.LOCALE_TEXT -> VisualLintEvent.IssueType.LOCALE_TEXT
    }
    CommonUsageTracker.getInstance(surface).logStudioEvent(LayoutEditorEvent.LayoutEditorEventType.VISUAL_LINT) {
      val builder = VisualLintEvent.newBuilder()
        .setIssueType(metricsIssueType)
        .setIssueEvent(issueEvent)
      it.visualLintEvent = builder.build()
    }
  }

  fun trackIssueCreation(issueType: VisualLintErrorType) {
    track(issueType, VisualLintEvent.IssueEvent.CREATE_ISSUE)
  }

  fun trackIssueExpanded(issue: Issue) {
    val issueType = when (issue) {
      is VisualLintAtfIssue -> VisualLintErrorType.ATF
      is VisualLintRenderIssue -> issue.type
      else -> null
    }
    if (issueType != null) {
      track(issueType, VisualLintEvent.IssueEvent.EXPAND_ISSUE)
    }
  }

  fun trackIssueIgnored(issueType: VisualLintErrorType) {
    track(issueType, VisualLintEvent.IssueEvent.IGNORE_ISSUE)
  }
}