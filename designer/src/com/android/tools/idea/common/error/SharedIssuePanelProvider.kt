/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.util.toPsiFile

class SharedIssuePanelProvider(private val project: Project) : ProblemsViewPanelProvider {
  override fun create(): ProblemsViewTab? {
    val problemsViewWindow = ProblemsView.getToolWindow(project) ?: return null
    return DesignerCommonIssuePanel(
      problemsViewWindow.disposable,
      project,
      !problemsViewWindow.anchor.isHorizontal,
      DEFAULT_SHARED_ISSUE_PANEL_TAB_TITLE,
      SHARED_ISSUE_PANEL_TAB_ID,
      { LayoutValidationNodeFactory },
      NotSuppressedFilter + SelectedEditorFilter(project),
      ::getEmptyMessage,
    )
  }

  private suspend fun getEmptyMessage(): String {
    val files = FileEditorManager.getInstance(project).selectedEditors.mapNotNull { it.file }
    if (files.isEmpty()) {
      return "No problems found"
    }

    val psiFiles = readAction { files.filter { it.isValid }.mapNotNull { it.toPsiFile(project) } }
    val fileNameString = files.joinToString { it.name }
    return if (
      psiFiles.size == files.size && psiFiles.all { LayoutFileType.isResourceTypeOf(it) }
    ) {
      "No layout problems in $fileNameString and qualifiers"
    } else {
      "No problems in $fileNameString"
    }
  }
}
