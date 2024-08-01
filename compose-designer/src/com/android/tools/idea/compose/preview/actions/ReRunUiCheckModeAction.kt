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
import com.android.tools.idea.common.surface.getDesignSurface
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.compose.preview.uicheck.TAB_IS_WEAR_PREVIEW
import com.android.tools.idea.compose.preview.uicheck.TAB_PREVIEW_DEFINITION
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.actions.getPreviewManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val DISABLED_TEXT = "UI Check is already running in the background."
private const val ENABLED_TEXT = "Restart UI Check and background linting for this composable."

/** This action restarts a terminated UI Check mode from the corresponding Problems window tab. */
class ReRunUiCheckModeAction : AnAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val uiCheckInstancePreviewDef =
      project?.let {
        ProblemsView.getToolWindow(it)
          ?.contentManager
          ?.selectedContent
          ?.getUserData(TAB_PREVIEW_DEFINITION)
      }

    if (
      uiCheckInstancePreviewDef == null ||
        runReadAction { uiCheckInstancePreviewDef.element == null }
    ) {
      e.presentation.isVisible = false
      return
    }
    val editors =
      FileEditorManager.getInstance(project).getAllEditors(uiCheckInstancePreviewDef.virtualFile)
    val isUiCheckRunning =
      editors
        .mapNotNull { it.getPreviewManager<ComposePreviewManager>() }
        .any { it.mode.value is PreviewMode.UiCheck }
    e.presentation.isVisible = true
    e.presentation.isEnabled = !isUiCheckRunning
    e.presentation.text = if (isUiCheckRunning) DISABLED_TEXT else ENABLED_TEXT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    // Gets the preview pointer associated with this UI Check
    val selectedContent = ProblemsView.getToolWindow(project)?.contentManager?.selectedContent
    val uiCheckInstancePreviewDef = selectedContent?.getUserData(TAB_PREVIEW_DEFINITION) ?: return
    val isWearPreview = selectedContent.getUserData(TAB_IS_WEAR_PREVIEW) ?: return

    // Selects or reopens the file containing the preview
    val editors =
      FileEditorManager.getInstance(project)
        .openFile(uiCheckInstancePreviewDef.virtualFile, true, true)
    val relevantEditor =
      editors.filterIsInstance<SplitEditor<*>>().firstOrNull {
        it.getPreviewManager<ComposePreviewManager>() != null
      } ?: return
    if (relevantEditor.isTextMode()) {
      relevantEditor.selectSplitMode(false)
    }

    val composeManager = relevantEditor.getPreviewManager<ComposePreviewManager>() ?: return
    val flowManager =
      relevantEditor.getDesignSurface()?.let { PreviewFlowManager.KEY.getData(
        CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, it)) } ?: return
    AndroidCoroutineScope(composeManager).launch {
      // Waits for the correct preview to be recreated, and starts UI Check on it
      val previewElements =
        flowManager.allPreviewElementsFlow.firstOrNull { flow ->
          flow.asCollection().any { it.previewElementDefinition == uiCheckInstancePreviewDef }
        }
      previewElements
        ?.asCollection()
        ?.firstOrNull { it.previewElementDefinition == uiCheckInstancePreviewDef }
        ?.let { composeManager.setMode(PreviewMode.UiCheck(UiCheckInstance(it, isWearPreview))) }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
