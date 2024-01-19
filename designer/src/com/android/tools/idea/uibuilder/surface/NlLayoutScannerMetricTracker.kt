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
import com.android.tools.idea.validator.ValidatorData
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.ApplyAtfFixEvent
import com.google.wireless.android.sdk.stats.AtfAuditResult
import com.google.wireless.android.sdk.stats.AtfFixDetail
import com.google.wireless.android.sdk.stats.AtfResultDetail
import com.google.wireless.android.sdk.stats.IgnoreAtfResultEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent

/** Metric tracker for results from accessibility testing framework */
class NlLayoutScannerMetricTracker(private val surface: NlDesignSurface) {

  /** Track expanded issues so we don't log them multiple times */
  @VisibleForTesting val expanded = HashSet<Issue>()

  /** Track the ignore button is clicked by user. */
  fun trackIgnoreButtonClicked(issue: ValidatorData.Issue) {
    CommonUsageTracker.getInstance(surface).logStudioEvent(
      LayoutEditorEvent.LayoutEditorEventType.IGNORE_ATF_RESULT
    ) { event ->
      event.setIgnoreAtfResultEvent(
        IgnoreAtfResultEvent.newBuilder().setAtfResult(atfResultDetailBuilder(issue))
      )
    }
  }

  /** Track the fix button is clicked by user. */
  fun trackApplyFixButtonClicked(issue: ValidatorData.Issue) {
    CommonUsageTracker.getInstance(surface).logStudioEvent(
      LayoutEditorEvent.LayoutEditorEventType.APPLY_ATF_FIX
    ) { event ->
      val applyAtfFixBuilder = ApplyAtfFixEvent.newBuilder()
      applyAtfFixBuilder.setAtfResult(atfResultDetailBuilder(issue))
      issue.mFix?.let { applyAtfFixBuilder.setAtfFix(atfFixDetailBuilder(it)) }
      event.setApplyAtfFixEvent(applyAtfFixBuilder)
    }
  }

  fun clear() {
    expanded.clear()
  }

  private fun atfResultDetailBuilder(issue: ValidatorData.Issue): AtfResultDetail.Builder {
    val resultType =
      when (issue.mLevel) {
        ValidatorData.Level.ERROR -> AtfAuditResult.AtfResultCount.CheckResultType.ERROR
        ValidatorData.Level.WARNING -> AtfAuditResult.AtfResultCount.CheckResultType.WARNING
        ValidatorData.Level.INFO -> AtfAuditResult.AtfResultCount.CheckResultType.INFO
        else -> AtfAuditResult.AtfResultCount.CheckResultType.UNKNOWN
      }
    return AtfResultDetail.newBuilder().setCheckName(issue.mSourceClass).setResultType(resultType)
  }

  private fun atfFixDetailBuilder(fix: ValidatorData.Fix): AtfFixDetail.Builder {
    return AtfFixDetail.newBuilder().setFixType(getAtfFixType(fix))
  }

  private fun getAtfFixType(fix: ValidatorData.Fix): AtfFixDetail.AtfFixType {
    return when (fix) {
      is ValidatorData.SetViewAttributeFix -> AtfFixDetail.AtfFixType.SET_VIEW_ATTRIBUTE
      is ValidatorData.RemoveViewAttributeFix -> AtfFixDetail.AtfFixType.REMOVE_VIEW_ATTRIBUTE
      is ValidatorData.CompoundFix -> AtfFixDetail.AtfFixType.COMPOUND
      else -> AtfFixDetail.AtfFixType.UNKNOWN
    }
  }
}

/** Metric metadata related to render results. */
data class RenderResultMetricData(
  var scanMs: Long = -1,
  var renderMs: Long = -1,
  var componentCount: Int = -1,
  var isRenderResultSuccess: Boolean = false,
) {}
