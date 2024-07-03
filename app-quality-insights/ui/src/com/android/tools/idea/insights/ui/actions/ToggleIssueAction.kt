/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.Permission
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleIssueAction(
  private val controller: AppInsightsProjectLevelController,
  private val state: AppInsightsState,
  private val issue: AppInsightsIssue,
) : AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.description = state.getActionDescription()
    e.presentation.isEnabled =
      when (issue.state) {
        IssueState.OPEN,
        IssueState.CLOSED -> state.shouldEnableAction()
        else -> false
      }
    e.presentation.text =
      when (issue.state) {
        IssueState.OPEN -> "Close issue"
        IssueState.OPENING -> "Opening..."
        IssueState.CLOSED -> "Undo close"
        IssueState.CLOSING -> "Closing..."
      }
  }

  override fun actionPerformed(e: AnActionEvent) {
    when (issue.state) {
      IssueState.OPEN -> controller.closeIssue(issue)
      IssueState.CLOSED -> controller.openIssue(issue)
      else -> Unit
    }
  }

  private fun AppInsightsState.shouldEnableAction() =
    permission == Permission.FULL && mode == ConnectionMode.ONLINE

  private fun AppInsightsState.getActionDescription() =
    when {
      permission != Permission.FULL ->
        "You don't have the necessary permissions to open/close issues."
      mode != ConnectionMode.ONLINE -> "AQI is offline."
      else -> null
    }
}
