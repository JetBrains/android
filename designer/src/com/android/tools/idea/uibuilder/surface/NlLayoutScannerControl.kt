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

import com.android.tools.idea.common.error.IssuePanel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.LayoutScannerControl
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable

/** Impl of [LayoutScannerControl] */
class NlLayoutScannerControl(
  private val surface: NlDesignSurface,
  disposable: Disposable
): LayoutScannerControl {

  /** Metric tracker for scanner */
  private val metricTracker = NlLayoutScannerMetricTracker(surface)

  /** The main scanner logic that parses atf results and creates lint issues */
  private val scanner = NlLayoutScanner(surface.issueModel, disposable, metricTracker)

  override val issues get() = scanner.issues

  override fun validateAndUpdateLint(renderResult: RenderResult, model: NlModel) {
    scanner.validateAndUpdateLint(renderResult, model, surface)
  }

  /** Listener for issue panel open/close */
  @VisibleForTesting
  val issuePanelListener = IssuePanel.MinimizeListener {
    // TODO: b/180069618 revisit metrics.
    // Logging when error panel closed/open is no longer as relevant since ATF always runs.
  }

  /** Listener when individual issues are expanded */
  private val issueExpandListener = IssuePanel.ExpandListener { issue, expanded ->
    if (scanner.issues.contains(issue)) {
      // TODO: b/180069618 revisit metrics.
      // Logging when error panel closed/open is no longer as relevant since ATF always runs.
    }
  }

  init {
    surface.issuePanel.addMinimizeListener(issuePanelListener)
    surface.issuePanel.expandListener = issueExpandListener
  }

  override fun pause() {
    LayoutValidator.setPaused(true)
  }

  override fun resume() {
    LayoutValidator.setPaused(false)
  }
}

// For debugging
fun ValidatorResult.toDetailedString(): String? {
  val builder: StringBuilder = StringBuilder().append("Result containing ").append(issues.size).append(
    " issues:\n")
  val var2: Iterator<*> = this.issues.iterator()
  while (var2.hasNext()) {
    val issue = var2.next() as ValidatorData.Issue
    if (issue.mLevel == ValidatorData.Level.ERROR) {
      builder.append(" - [E::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
    else {
      builder.append(" - [W::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
  }
  return builder.toString()
}