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

import com.android.tools.idea.common.editor.SplitEditor
import com.android.tools.idea.common.error.DESIGNER_COMMON_ISSUE_PANEL
import com.android.tools.idea.common.error.DesignToolsIssueProvider
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.compose.preview.getComposePreviewManager
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.modes.PreviewMode
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val DISABLED_TEXT = "UI Check is already running in the background."
private const val ENABLED_TEXT = "Restart UI Check and background linting for this composable."

class ReRunUiCheckModeAction : AnAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file =
      project?.let {
        ProblemsView.getToolWindow(it)
          ?.contentManager
          ?.selectedContent
          ?.getUserData(IssuePanelService.TAB_VIRTUAL_FILE)
      }
    if (file == null) {
      e.presentation.isVisible = false
      return
    }
    val editors = FileEditorManager.getInstance(project).getAllEditors(file)
    val isUiCheckRunning =
      editors
        .mapNotNull { it.getComposePreviewManager() }
        .any { it.mode.value is PreviewMode.UiCheck }
    e.presentation.isVisible = true
    e.presentation.isEnabled = !isUiCheckRunning
    e.presentation.text = if (isUiCheckRunning) DISABLED_TEXT else ENABLED_TEXT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val instanceId =
      (e.getData(DESIGNER_COMMON_ISSUE_PANEL)?.issueProvider as? DesignToolsIssueProvider)
        ?.instanceId ?: return
    val file =
      ProblemsView.getToolWindow(project)
        ?.contentManager
        ?.selectedContent
        ?.getUserData(IssuePanelService.TAB_VIRTUAL_FILE) ?: return
    val editors = FileEditorManager.getInstance(project).openFile(file, true, true)
    val relevantEditor =
      editors.filterIsInstance<SplitEditor<*>>().firstOrNull {
        it.getComposePreviewManager() != null
      } ?: return
    if (relevantEditor.isTextMode()) {
      relevantEditor.selectSplitMode(false)
    }
    val manager = relevantEditor.getComposePreviewManager() ?: return
    AndroidCoroutineScope(manager).launch {
      manager.allPreviewElementsInFileFlow.collectLatest { flow ->
        flow
          .asCollection()
          .firstOrNull { it.instanceId == instanceId }
          ?.let { manager.setMode(PreviewMode.UiCheck(it)) }
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
