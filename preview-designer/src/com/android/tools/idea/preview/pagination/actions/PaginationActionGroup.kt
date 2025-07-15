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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator

/**
 * Group of actions for users to interact with the pagination mechanism of a [PreviewRepresentation]
 * that supports it ([PreviewPaginationManager]).
 */
class PaginationActionGroup :
  DefaultActionGroup(PreviewBundle.message("action.preview.pagination.title"), false) {

  init {
    add(PageSizeSelectorAction())
    add(Separator.getInstance())
    add(PreviousPageAction())
    add(CurrentPageEditorAction())
    add(NextPageAction())
    add(Separator.getInstance())
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val manager = e.dataContext.getData(PreviewPaginationManager.Companion.KEY)
    val presentation = e.presentation
    // Only show the actions if a manager is available and initialized
    presentation.isVisible = manager != null && manager.getTotalPages() != null
  }
}
