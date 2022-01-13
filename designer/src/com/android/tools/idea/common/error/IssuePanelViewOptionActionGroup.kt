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

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel
import org.jetbrains.annotations.Nls

class IssuePanelViewOptionActionGroup : ActionGroup(), DumbAware {
  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val project = event?.project ?: return AnAction.EMPTY_ARRAY
    if (project.isDisposed) return AnAction.EMPTY_ARRAY
    return SeverityRegistrar.getSeverityRegistrar(project).allSeverities.reversed()
      .filter { it != HighlightSeverity.INFO && it > HighlightSeverity.INFORMATION && it < HighlightSeverity.ERROR }
      .map { SeverityFilterAction(project, "Show " + SingleInspectionProfilePanel.renderSeverity(it), it.myVal) }
      .toTypedArray()
  }
}

private class SeverityFilterAction(val project: Project, @Nls name: String, val severity: Int) : DumbAwareToggleAction(name) {
  override fun isSelected(event: AnActionEvent) = !ProblemsViewState.getInstance(project).hideBySeverity.contains(severity)
  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val panel = IssuePanelService.getInstance(project).getSelectedSharedIssuePanel() ?: return
    val state = ProblemsViewState.getInstance(project)
    val changed = with(state.hideBySeverity) { if (selected) remove(severity) else add(severity) }
    if (changed) {
      panel.setHiddenSeverities(state.hideBySeverity)
      state.intIncrementModificationCount()
    }
  }
}
