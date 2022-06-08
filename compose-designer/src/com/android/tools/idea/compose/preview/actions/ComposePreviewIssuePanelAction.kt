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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssuePanelService.Companion.getInstance
import com.android.tools.idea.common.error.NlIssueSource
import com.android.tools.idea.common.error.setIssuePanelVisibility
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons
import javax.swing.Icon

fun Collection<Issue>.getIssueTypeIcon(): Icon? {
  return groupBy { it.severity }.let {
    when {
      it[HighlightSeverity.ERROR]?.any() ?: false -> StudioIcons.Common.ERROR
      it[HighlightSeverity.WARNING]?.any() ?: false -> StudioIcons.Common.WARNING
      it[HighlightSeverity.INFORMATION]?.any() ?: false -> StudioIcons.Common.INFO
      else -> null
    }
  }
}

/**
 * An action that shows the status of the issues for the given [NlModel]. The action will look at the issues in
 * the provided [IssueModel] to decide the icon. Clicking the icon will open the issue panel.
 */
class ComposePreviewIssuePanelAction(
  private val modelProvider: () -> NlModel?,
  private val issueModelProvider: () -> IssueModel?): ToggleAction() {

  // This action never changes state.
  override fun isSelected(e: AnActionEvent): Boolean = false

  override fun update(e: AnActionEvent) {
    super.update(e)

    val model = modelProvider()
    val issueModel = issueModelProvider()
    if (model == null || issueModel == null) {
      e.presentation.isVisible = false
      return
    }

    val icon = issueModel.issues
      .filter {
        (it.source as? NlIssueSource)?.model == model
      }.getIssueTypeIcon()
    e.presentation.icon = icon
    e.presentation.isEnabledAndVisible = icon != null
  }
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(DESIGN_SURFACE)?.setIssuePanelVisibility(state, true)
  }
}