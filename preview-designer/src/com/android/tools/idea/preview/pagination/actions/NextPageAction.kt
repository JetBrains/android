/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.preview.pagination.actions

import com.android.tools.idea.preview.PreviewBundle
import com.android.tools.idea.preview.pagination.PreviewPaginationManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to increment by one the current page value in a [PreviewRepresentation] that supports
 * pagination ([PreviewPaginationManager]).
 */
class NextPageAction :
  AnAction(
    PreviewBundle.message("action.preview.pagination.next.page.name"),
    PreviewBundle.message("action.preview.pagination.next.page.description"),
    AllIcons.Actions.Forward,
  ) {

  override fun actionPerformed(e: AnActionEvent) {
    val manager = e.dataContext.getData(PreviewPaginationManager.Companion.KEY) ?: return
    val totalPages = manager.getTotalPages() ?: return
    if (manager.selectedPage < totalPages - 1) {
      manager.selectedPage += 1
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val manager = e.dataContext.getData(PreviewPaginationManager.Companion.KEY) ?: return
    val totalPages = manager.getTotalPages() ?: return
    presentation.isEnabled = manager.selectedPage < totalPages - 1
  }
}
