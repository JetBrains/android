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

import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintSettings
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel
import org.jetbrains.annotations.Nls

class IssuePanelViewOptionActionGroup : ActionGroup(), DumbAware {
  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val project = event?.project ?: return AnAction.EMPTY_ARRAY
    if (project.isDisposed) return AnAction.EMPTY_ARRAY
    val severityViewOptions: Array<AnAction> =
      SeverityRegistrar.getSeverityRegistrar(project)
        .allSeverities
        .reversed()
        .filter {
          it != HighlightSeverity.INFO &&
            it > HighlightSeverity.INFORMATION &&
            it < HighlightSeverity.ERROR
        }
        .map {
          SeverityFilterAction("Show " + SingleInspectionProfilePanel.renderSeverity(it), it.myVal)
        }
        .toTypedArray()
    val visualLintViewOption: Array<AnAction> = arrayOf(VisualLintFilterAction())
    val toggleViewOptions = severityViewOptions + visualLintViewOption

    val separator = arrayOf(Separator.create())

    val toggleOrderOptions =
      arrayOf<AnAction>(
        ToggleIssuePanelSortedBySeverityAction(),
        ToggleIssuePanelSortedByNameAction(),
      )

    return toggleViewOptions + separator + toggleOrderOptions
  }
}

/** These actions are associated to Intellij's problems panel. */
class SeverityFilterAction(@Nls name: String, val severity: Int) : DumbAwareToggleAction(name) {
  override fun isSelected(event: AnActionEvent): Boolean {
    val project = event.project ?: return false
    return !ProblemsViewState.getInstance(project).hideBySeverity.contains(severity)
  }

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val project = event.project ?: return
    val state = ProblemsViewState.getInstance(project)
    val changed = with(state.hideBySeverity) { if (selected) remove(severity) else add(severity) }
    if (changed) {
      state.intIncrementModificationCount()
      updateIssuePanelFilter(event)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class VisualLintFilterAction : DumbAwareToggleAction("Show Screen Size Problem") {
  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return VisualLintSettings.getInstance(project).isVisualLintFilterSelected
  }

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    val project = e.project ?: return
    VisualLintSettings.getInstance(project).isVisualLintFilterSelected = selected
    updateIssuePanelFilter(e)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private fun updateIssuePanelFilter(e: AnActionEvent) =
  e.dataContext.getData(DESIGNER_COMMON_ISSUE_PANEL)?.updateIssueVisibility()

class ToggleIssuePanelSortedBySeverityAction : DumbAwareToggleAction("Sort By Severity") {

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return ProblemsViewState.getInstance(project).sortBySeverity
  }

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    val project = e.project ?: return
    val state = ProblemsViewState.getInstance(project)
    state.sortBySeverity = selected
    state.intIncrementModificationCount()
    updateIssuePanelOrder(e)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class ToggleIssuePanelSortedByNameAction : DumbAwareToggleAction("Sort By Name") {

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return ProblemsViewState.getInstance(project).sortByName
  }

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    val project = e.project ?: return
    val state = ProblemsViewState.getInstance(project)
    state.sortByName = selected
    state.intIncrementModificationCount()
    updateIssuePanelOrder(e)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private fun updateIssuePanelOrder(e: AnActionEvent) =
  e.dataContext.getData(DESIGNER_COMMON_ISSUE_PANEL)?.updateIssueOrder()
