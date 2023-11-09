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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

private const val DISABLED_TEXT = "UI Check is already running in the background."
private const val ENABLED_TEXT = "Restart UI Check and background linting for this composable."

class ReRunUiCheckModeAction : AnAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val dataContext = uiTabDataContext(project) ?: return
    val manager = dataContext.getData(COMPOSE_PREVIEW_MANAGER.name) as? ComposePreviewManager
    e.presentation.isVisible = manager != null
    manager?.let {
      if (it.mode.value is PreviewMode.UiCheck) {
        e.presentation.isEnabled = false
        e.presentation.text = DISABLED_TEXT
      } else {
        e.presentation.isEnabled = true
        e.presentation.text = ENABLED_TEXT
      }
    }
    e.presentation.isEnabled = manager?.mode?.value !is PreviewMode.UiCheck

    super.update(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dataContext = uiTabDataContext(project) ?: return
    val manager =
      dataContext.getData(COMPOSE_PREVIEW_MANAGER.name) as? ComposePreviewManager ?: return
    val instance =
      dataContext.getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE.name) as? ComposePreviewElementInstance
        ?: return
    manager.setMode(PreviewMode.UiCheck(baseElement = instance))
    instance.containingFile?.let {
      FileEditorManager.getInstance(project).openFile(it.virtualFile, true, true)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private fun uiTabDataContext(project: Project): DataProvider? {
    val tabComponent =
      ProblemsView.getToolWindow(project)?.contentManagerIfCreated?.selectedContent?.component
        ?: return null
    return DataManagerImpl.getDataProviderEx(tabComponent)
  }
}
