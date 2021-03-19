/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.error.Issue
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AtfAuditResult
import com.google.wireless.android.sdk.stats.LayoutEditorEvent

/** Metric tracker for results from accessibility testing framework */
class NlLayoutScannerMetricTracker(private val surface: NlDesignSurface) {

  /** Track expanded issues so we don't log them multiple times */
  @VisibleForTesting
  val expanded = HashSet<Issue>()

  /** Tracks all issues created by atf. */
  fun trackIssues(issues: Set<Issue>, renderMetric: RenderResultMetricData) {
    if (!renderMetric.isValid() || issues.isEmpty()) {
      return
    }

    val atfIssues = issues.filterIsInstance<NlAtfIssue>()
    if (atfIssues.isEmpty()) {
      return
    }

    CommonUsageTracker.getInstance(surface).logStudioEvent(
      LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT) { event ->
      val atfResultBuilder = AtfAuditResult.newBuilder()
      atfIssues.forEach { issue ->
        atfResultBuilder.addCounts(builder(issue))
      }
      atfResultBuilder
        .setAuditDurationMs(renderMetric.scanMs)
        .setTotalRenderTimeMs(renderMetric.renderMs)
        .setComponentCount(renderMetric.componentCount)
        .setRenderResult(renderMetric.isRenderResultSuccess)
      event.setAtfAuditResult(atfResultBuilder)
    }
  }

  /** Track the first time the issue is expanded by user. */
  fun trackFirstExpanded(issue: Issue) {
    if (issue !is NlAtfIssue || expanded.contains(issue)) {
      return
    }

    expanded.add(issue)

    CommonUsageTracker.getInstance(surface).logStudioEvent(
      LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT) { event ->
      val atfResultBuilder = AtfAuditResult.newBuilder()
      atfResultBuilder.addCounts(builder(issue).setErrorExpanded(true))
      event.setAtfAuditResult(atfResultBuilder)
    }
  }

  fun clear() {
    expanded.clear()
  }

  private fun builder(issue: NlAtfIssue): AtfAuditResult.AtfResultCount.Builder {
    return AtfAuditResult.AtfResultCount.newBuilder().setCheckName(issue.srcClass)
  }
}

/** Metric metadata related to render results. */
data class RenderResultMetricData(
  var scanMs: Long = 0,
  var renderMs: Long = 0,
  var componentCount: Int = 0,
  var isRenderResultSuccess: Boolean = false) {

  /** True if metric is valid. False otherwise. */
  fun isValid(): Boolean {
    return scanMs > 0 && renderMs > 0 && componentCount > 0
  }
}