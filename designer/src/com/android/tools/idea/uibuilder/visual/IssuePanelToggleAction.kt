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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.uibuilder.visual.analytics.trackLayoutValidationToggleIssuePanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

private const val BUTTON_TEXT = "Toggle visibility of issue panel"

class IssuePanelToggleAction :
  ToggleAction(BUTTON_TEXT, BUTTON_TEXT, StudioIcons.Common.WARNING_INLINE) {

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return IssuePanelService.getInstance(project).isIssuePanelVisible()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val issuePanelService = e.project?.let { IssuePanelService.getInstance(it) } ?: return
    issuePanelService.setSharedIssuePanelVisibility(state) {
      issuePanelService.focusIssuePanelIfVisible()
      // Track as Layout Validation Too event.
      e.getData(DESIGN_SURFACE)?.let { trackLayoutValidationToggleIssuePanel(it, state) }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.getData(PlatformDataKeys.PROJECT)?.let { project ->
      e.presentation.isVisible =
        IssuePanelService.getInstance(project).getSharedPanelIssues()?.size != 0
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
