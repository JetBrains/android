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
package com.android.tools.idea.common.error

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction

class ToggleIssueDetailAction : ToggleAction() {
  // This action uses ActionUpdateThread.EDT as it checks a panel visibility. The check needs to
  // happen in EDT.
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun isEnabled(e: AnActionEvent): Boolean =
    e.project?.let { IssuePanelService.getInstance(it).getSelectedIssuePanel() } != null &&
      e.dataContext.getData(PlatformDataKeys.SELECTED_ITEM) as? IssueNode != null

  override fun update(e: AnActionEvent) {
    super.update(e)

    with(e.presentation) {
      text = "Show Issue Detail"
      isVisible = true
      icon = AllIcons.Actions.PreviewDetails
      isEnabled = isEnabled(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    if (!isEnabled(e)) return false

    val issuePanel = e.project?.let { IssuePanelService.getInstance(it).getSelectedIssuePanel() }
    return issuePanel?.sidePanelVisible ?: false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val issuePanel =
      e.project?.let { IssuePanelService.getInstance(it).getSelectedIssuePanel() } ?: return
    issuePanel.sidePanelVisible = state
  }
}
