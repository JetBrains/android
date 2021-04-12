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

import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

private const val BUTTON_TEXT = "Toggle visibility of issue panel"

class IssuePanelToggleAction(val surface: NlDesignSurface) : ToggleAction(BUTTON_TEXT, BUTTON_TEXT, StudioIcons.Common.WARNING) {

  override fun isSelected(e: AnActionEvent) = !surface.issuePanel.isMinimized

  override fun setSelected(e: AnActionEvent, state: Boolean) = surface.setShowIssuePanel(state, false)

  override fun update(e: AnActionEvent) {
    super.update(e)
    // TODO(b/184734874): figure out whether to hide the action or disable it when there are no issues
    e.presentation.isVisible = surface.issueModel.hasIssues()
  }
}
