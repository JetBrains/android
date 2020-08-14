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
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AtfAuditResult
import com.intellij.openapi.Disposable
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture

/** Impl of [LayoutScannerControl] configured with [LayoutScannerAction] */
class NlLayoutScannerControl: LayoutScannerControl {

  constructor(designSurface: NlDesignSurface) {
    surface = designSurface
    metricTracker = NlLayoutScannerMetricTracker(surface)
    myScanner = NlLayoutScanner(surface.issueModel, surface, metricTracker)
    init()
  }

  /** For testing purposes. Mocked values cannot be disposed in junit it seems. */
  @TestOnly
  constructor(designSurface: NlDesignSurface, disposable: Disposable) {
    surface = designSurface
    metricTracker = NlLayoutScannerMetricTracker(surface)
    myScanner = NlLayoutScanner(surface.issueModel, disposable, metricTracker)
    init()
  }

  override val scanner get() = myScanner

  private lateinit var myScanner: NlLayoutScanner
  private lateinit var surface: NlDesignSurface
  private lateinit var metricTracker: NlLayoutScannerMetricTracker

  private var scannerResult: CompletableFuture<Boolean>? = null

  /** Listener for issue panel open/close */
  @VisibleForTesting
  val issuePanelListener = IssuePanel.MinimizeListener {
    val check = surface.sceneManager?.layoutScannerConfig ?: return@MinimizeListener

    if (it) {
      // Minimized
      if (!alwaysEnableLayoutScanner) {
        scanner.disable()
        check.isLayoutScannerEnabled = false
      }
    }
    else if (!check.isLayoutScannerEnabled) {
      metricTracker.trackTrigger(AtfAuditResult.Trigger.ISSUE_PANEL)
      tryRefreshWithScanner()
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

  private fun init() {
    surface.issuePanel.addMinimizeListener(issuePanelListener)
    surface.issuePanel.expandListener = issueExpandListener
  }

  override fun runLayoutScanner(): CompletableFuture<Boolean> {
    scanner.addListener(scannerListener)
    if (!tryRefreshWithScanner()) {
      return CompletableFuture.completedFuture(false)
    }
    // TODO: b/162528405 Fix this at some point. For now calling this function multiple times sequentially would cause
    //  some events to be ignored. I need to invest in direct path from requestRender to scanner listener.
    //  render complete does not guarentee error panel updated.
    scannerResult = CompletableFuture()
    metricTracker.trackTrigger(AtfAuditResult.Trigger.USER)
    return scannerResult!!
  }

  /**
   * Attempt to run refresh on surface with scanner on.
   * Returns true if the request was sent successfully false otherwise.
   */
  @VisibleForTesting
  fun tryRefreshWithScanner(): Boolean {
    val manager = surface.sceneManager ?: return false
    manager.layoutScannerConfig.isLayoutScannerEnabled = true
    manager.forceReinflate()
    surface.requestRender()
    return true
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