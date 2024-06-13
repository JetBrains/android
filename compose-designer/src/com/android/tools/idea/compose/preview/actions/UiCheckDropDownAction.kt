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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.error.IssueProviderListener
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.wm.ToolWindowManager

class UiCheckDropDownAction :
  DropDownAction(
    message("action.uicheck.toolbar.title"),
    message("action.uicheck.toolbar.description"),
    AllIcons.General.ChevronDown,
  ) {
  init {
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
  }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    context.getData(COMPOSE_PREVIEW_MANAGER)?.let {
      add(UiCheckFilteringAction(it))
      addSeparator()
      add(UiCheckReopenTabAction(it))
    }
    return true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

internal class UiCheckFilteringAction(private val previewManager: ComposePreviewManager) :
  ToggleAction("Show Previews With Problems Only") {

  override fun isSelected(e: AnActionEvent) = previewManager.isUiCheckFilterEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    previewManager.isUiCheckFilterEnabled = state
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

internal class UiCheckReopenTabAction(private val previewManager: ComposePreviewManager) :
  AnAction("Open UI Check Tab in Problems Panel") {

  /** Running on EDT since the update accesses UI state via tabName */
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val problemsWindow =
      ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID) ?: return
    val uiCheckInstance =
      (previewManager.mode.value as? PreviewMode.UiCheck)?.baseInstance ?: return
    val previewInstance = uiCheckInstance.baseElement as? ComposePreviewElementInstance ?: return
    val tab =
      problemsWindow.contentManager.contents.firstOrNull {
        it.tabName == previewInstance.instanceId
      }
    if (tab != null) {
      problemsWindow.contentManager.setSelectedContent(tab)
    } else {
      (previewManager as? ComposePreviewRepresentation)?.createUiCheckTab(
        previewInstance,
        uiCheckInstance.isWearPreview,
      )
      VisualLintService.getInstance(project)
        .issueModel
        .updateErrorsList(IssueProviderListener.UI_CHECK)
      ProblemsViewToolWindowUtils.selectTab(project, previewInstance.instanceId)
    }
    problemsWindow.show()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val problemsWindow =
      ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID) ?: return
    val previewInstance =
      (previewManager.mode.value as? PreviewMode.UiCheck)?.baseInstance?.baseElement
        as? ComposePreviewElementInstance ?: return
    e.presentation.isEnabled =
      !problemsWindow.isVisible ||
        problemsWindow.contentManager.selectedContent?.tabName != previewInstance.instanceId
  }
}
