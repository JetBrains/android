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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * Helper method that navigates back to the previous [PreviewMode] for all [PreviewModeManager]s in
 * the given [AnActionEvent]'s [DataContext].
 *
 * @param e the [AnActionEvent] holding the context of the action
 */
fun navigateBack(e: AnActionEvent) {
  findPreviewModeManagersForContext(e.dataContext).forEach { it.restorePrevious() }
}

/**
 * Returns a list of all [PreviewModeManager]s related to the current context (which is implied to
 * be bound to a particular file). The search is done among the open preview parts and
 * [PreviewRepresentation]s (if any) of open file editors.
 *
 * This call might access the [CommonDataKeys.VIRTUAL_FILE] so it should not be called in the EDT
 * thread. For actions using it, they should use [ActionUpdateThread.BGT].
 */
internal fun findPreviewModeManagersForContext(context: DataContext): List<PreviewModeManager> {
  context.getData(PreviewModeManager.KEY)?.let {
    // The context is associated to a PreviewModeManager so return it
    return listOf(it)
  }

  // Fallback to finding the PreviewModeManager by looking into all the editors
  val project = context.getData(CommonDataKeys.PROJECT) ?: return emptyList()
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()

  return FileEditorManager.getInstance(project)?.getAllEditors(file)?.mapNotNull {
    it.getPreviewModeManager()
  }
    ?: emptyList()
}

/**
 * Returns the [PreviewModeManager] or null if this [FileEditor]'s preview representation is not a
 * [PreviewModeManager].
 */
private fun FileEditor.getPreviewModeManager(): PreviewModeManager? =
  when (this) {
    is MultiRepresentationPreview -> this.currentRepresentation as? PreviewModeManager
    is TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview> ->
      this.preview.currentRepresentation as? PreviewModeManager
    else -> null
  }
