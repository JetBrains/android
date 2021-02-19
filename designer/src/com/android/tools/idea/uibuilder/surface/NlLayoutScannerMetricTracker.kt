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
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AtfAuditResult
import com.google.wireless.android.sdk.stats.AtfAuditResult.AtfResultCount.CheckResultType
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import java.util.function.Consumer
import java.util.stream.Stream

/**
 * Metric tracker for results from accessibility testing framework
 */
class NlLayoutScannerMetricTracker(
  private val surface: NlDesignSurface) {
  @VisibleForTesting
  val metric = ScannerMetrics()
  @VisibleForTesting
  val expandedTracker = IssueExpansionMetric()

  /** Tracks who triggered the scanner */
  fun trackTrigger(trigger: AtfAuditResult.Trigger) {
    metric.trigger = trigger
  }

  /** Tracks scanning result */
  fun trackResult(result: RenderResult, validatorResult: ValidatorResult) {
    metric.renderMs = result.stats.totalRenderDurationMs
    metric.scanMs = validatorResult?.metric?.mElapsedMs ?: 0
    metric.componentCount = result.rootViews.stream().flatMap {
      Stream.concat(it.children.stream(), Stream.of(it)) }.count().toInt()
    metric.errorCounts = surface.issueModel.issueCount
    metric.isRenderResultSuccess = result.renderResult.isSuccess
  }

  /** Log all metrics gathered */
  fun logEvents() {
    metric.logEvent(CommonUsageTracker.getInstance(surface))
  }

  /** Tracks issues expanded */
  fun trackIssueExpanded(issue: Issue?, expanded: Boolean) {
    if (issue is NlAtfIssue && expanded) {
      expandedTracker.opened.add(issue.srcClass)
    }
  }

  fun trackIssuePanelClosed() {
    expandedTracker.logEvent(CommonUsageTracker.getInstance(surface))
  }

  /** Tracks individual issue */
  fun trackIssue(issue: ValidatorData.Issue) {
    val countBuilder = AtfAuditResult.AtfResultCount.newBuilder()
      .setResultType(convert(issue.mLevel))
      .setCheckName(issue.mSourceClass)
    metric.counts.add(countBuilder)
  }

  /** Convert from layoutlib understood level to proto understood level */
  private fun convert(level: ValidatorData.Level): CheckResultType {
    return when(level) {
      ValidatorData.Level.ERROR -> CheckResultType.ERROR
      ValidatorData.Level.WARNING -> CheckResultType.WARNING
      ValidatorData.Level.INFO -> CheckResultType.INFO
      ValidatorData.Level.VERBOSE -> CheckResultType.NOT_RUN
    }
  }
}

data class IssueExpansionMetric(val opened: MutableList<String> = ArrayList()) {

  /** Logs events using the usage tracker passed, and clear. */
  fun logEvent(usageTracker: CommonUsageTracker) {
    val counts = opened.stream().flatMap {
      val builder = AtfAuditResult.AtfResultCount.newBuilder()
          .setCheckName(it)
          .setErrorExpanded(true)
      Stream.of(builder)
    }
    usageTracker.logStudioEvent(
      LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT,
      Consumer<LayoutEditorEvent.Builder> { event ->
        val atfResultBuilder = AtfAuditResult.newBuilder()
        counts.forEach { countBuilder ->
          atfResultBuilder.addCounts(countBuilder)
        }
        event.setAtfAuditResult(atfResultBuilder)
      })

    opened.clear()
  }
}

/** Data class for all scanner related metrics */
data class ScannerMetrics(
  var trigger: AtfAuditResult.Trigger = AtfAuditResult.Trigger.UNKNOWN_TRIGGER,
  var scanMs: Long = 0,
  var renderMs: Long = 0,
  var errorCounts: Int = 0,
  var isRenderResultSuccess: Boolean = true,
  var componentCount: Int = 0
) {
  val counts = ArrayList<AtfAuditResult.AtfResultCount.Builder>()

  /** Fire all the locally logged events to the server, then clear any local remains. */
  fun logEvent(usageTracker: CommonUsageTracker) {
    usageTracker.logStudioEvent(
      LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT,
      Consumer<LayoutEditorEvent.Builder> { event ->
        val atfResultBuilder = AtfAuditResult.newBuilder()
        counts.forEach { countBuilder ->
          atfResultBuilder.addCounts(countBuilder)
        }
        atfResultBuilder
          .setTrigger(trigger)
          .setComponentCount(componentCount)
          .setRenderResult(isRenderResultSuccess)
          .setAuditDurationMs(scanMs)
          .setTotalRenderTimeMs(renderMs)
          .setErrorCount(errorCounts)

        event.setAtfAuditResult(atfResultBuilder)
      })

    clear()
  }

  /** Remove any previous events */
  private fun clear() {
    trigger = AtfAuditResult.Trigger.UNKNOWN_TRIGGER
    scanMs = 0
    renderMs = 0
    errorCounts = 0
    isRenderResultSuccess = true
    componentCount = 0
    counts.clear()
  }
}