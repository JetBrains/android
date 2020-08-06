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
import com.android.tools.idea.ui.alwaysEnableLayoutScanner
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import java.util.concurrent.CompletableFuture

/** Impl of [LayoutScannerControl] configured with [LayoutScannerAction] */
class NlLayoutScannerControl(private val surface: NlDesignSurface): LayoutScannerControl {

  override val scanner = NlLayoutScanner(surface.issueModel, surface)

  private val metricTracker = NlLayoutScannerMetricTracker()

  /** Listener for issue panel open/close */
  private val issuePanelListener = IssuePanel.MinimizeListener {
    val check = surface.sceneManager?.layoutScannerConfig ?: return@MinimizeListener

    if (it) {
      // Minimized
      if (!alwaysEnableLayoutScanner) {
        scanner.disable()
        check.isLayoutScannerEnabled = false
      }
    }
    else if (!check.isLayoutScannerEnabled) {
      check.isLayoutScannerEnabled = true
      surface.forceUserRequestedRefresh()
    }
  }

  private val issueExpandListener = IssuePanel.ExpandListener {issue, expanded ->
    if (scanner.issues.contains(issue)) {
      metricTracker.trackIssueExpanded(issue, expanded)
    }
  }

  private val scannerListener = object : NlLayoutScanner.Listener {
    override fun lintUpdated(result: ValidatorResult?) {
      if (result != null) {
        try {
          if (result.issues.isEmpty()) {
            // Nothing to show
            scannerResult?.complete(false)
            return
          }
          // Has result to display
          surface.analyticsManager.trackShowIssuePanel()
          surface.setShowIssuePanel(true)
          scannerResult?.complete(true)
        } finally {
          scanner.removeListener(this)
          scannerResult = null
        }
      }
    }
  }

  private var scannerResult: CompletableFuture<Boolean>? = null

  init {
    surface.issuePanel.addMinimizeListener(issuePanelListener)
    surface.issuePanel.expandListener = issueExpandListener
  }

  override fun runLayoutScanner(): CompletableFuture<Boolean> {
    scanner.addListener(scannerListener)
    val manager = surface.sceneManager ?: return CompletableFuture.completedFuture(false)
    // TODO: b/162528405 Fix this at some point. For now calling this function multiple times sequentially would cause
    //  some events to be ignored. I need to invest in direct path from requestRender to scanner listener.
    //  render complete does not guarentee error panel updated.
    scannerResult = CompletableFuture()
    manager.layoutScannerConfig?.isLayoutScannerEnabled = true
    manager.forceReinflate()
    surface.requestRender()
    return scannerResult!!
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
    } else {
      builder.append(" - [W::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
  }
  return builder.toString()
}