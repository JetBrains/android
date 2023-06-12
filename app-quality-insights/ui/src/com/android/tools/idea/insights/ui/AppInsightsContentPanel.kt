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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CancellableTimeoutException
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.RevertibleException
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import java.lang.Integer.min
import javax.swing.JPanel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppInsightsContentPanel(
  private val projectController: AppInsightsProjectLevelController,
  project: Project,
  parentDisposable: Disposable,
  tracker: AppInsightsTracker,
  cellRenderer: AppInsightsTableCellRenderer,
  secondaryToolWindows: List<ToolWindowDefinition<AppInsightsToolWindowContext>>,
  getConsoleUrl: (Connection, Pair<Long, Long>?, Set<Version>, IssueDetails) -> String
) : JPanel(BorderLayout()), Disposable {
  private val issuesState: Flow<LoadingState<Selection<AppInsightsIssue>>> =
    projectController.state.map { it.issues.map { timed -> timed.value } }.distinctUntilChanged()
  private val issuesTableView: AppInsightsIssuesTableView

  init {
    Disposer.register(parentDisposable, this)
    val issuesModel = AppInsightsIssuesTableModel(cellRenderer)
    issuesTableView =
      AppInsightsIssuesTableView(
        issuesModel,
        projectController,
        cellRenderer,
        this::handleException
      )
    Disposer.register(this, issuesTableView)
    val mainContentPanel = JPanel(BorderLayout())
    mainContentPanel.add(
      StackTracePanel(
        projectController,
        issuesState,
        project,
        issuesTableView::setHeaderHeight,
        this,
        tracker,
        getConsoleUrl
      )
    )

    val splitter =
      ThreeComponentsSplitter(false, true, this).apply {
        setHonorComponentsMinimumSize(true)
        firstComponent = issuesTableView.component
        innerComponent = mainContentPanel
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(APP_INSIGHTS_ID)
        firstSize = min((toolWindow?.component?.width ?: 1350) / 3, 700)
      }
    splitter.isFocusCycleRoot = false
    val workBench = WorkBench<AppInsightsToolWindowContext>(project, APP_INSIGHTS_ID, null, this)
    workBench.isFocusCycleRoot = false
    workBench.init(splitter, AppInsightsToolWindowContext(), secondaryToolWindows, false)

    add(workBench)
  }

  private fun handleException(failure: LoadingState.Failure): Boolean {
    val cause = failure.cause
    if (cause is RevertibleException) {
      when (val revertibleCause = cause.cause) {
        is CancellableTimeoutException -> {
          // TODO: Add loading spinner
          issuesTableView.table.tableEmptyText.apply {
            clear()
            appendText("Fetching issues is taking longer than expected.", EMPTY_STATE_TITLE_FORMAT)

            if (StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) {
              appendSecondaryText("You can wait, ", EMPTY_STATE_TEXT_FORMAT, null)
              appendSecondaryText("retry", EMPTY_STATE_LINK_FORMAT) { projectController.refresh() }
              appendSecondaryText(" or ", EMPTY_STATE_TEXT_FORMAT, null)
              appendSecondaryText("enter offline mode", EMPTY_STATE_LINK_FORMAT) {
                projectController.enterOfflineMode()
              }
              appendSecondaryText(" to see cached data.", EMPTY_STATE_TEXT_FORMAT, null)
            } else if (cause.snapshot != null) {
              appendSecondaryText("You can wait or ", EMPTY_STATE_TEXT_FORMAT, null)
              appendSecondaryText("cancel the request", EMPTY_STATE_LINK_FORMAT) {
                projectController.revertToSnapshot(cause.snapshot as AppInsightsState)
              }
            }
          }
        }
        else -> {
          issuesTableView.table.tableEmptyText.apply {
            clear()
            appendText(
              failure.message ?: revertibleCause?.message ?: "An unknown failure occurred",
              EMPTY_STATE_TITLE_FORMAT
            )
            if (cause.snapshot != null) {
              appendSecondaryText("Go Back", EMPTY_STATE_LINK_FORMAT) {
                projectController.revertToSnapshot(cause.snapshot as AppInsightsState)
              }
            }
          }
        }
      }
      return true
    }
    return false
  }

  override fun dispose() = Unit
}
