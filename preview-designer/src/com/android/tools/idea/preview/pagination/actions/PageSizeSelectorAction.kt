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
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import javax.swing.JComponent

/**
 * Action to change the size of each page to be used in a [PreviewRepresentation] that supports
 * pagination ([PreviewPaginationManager]).
 */
class PageSizeSelectorAction : ComboBoxAction() {

  private val pageSizes = listOf(10, 20, 50, 100, 200)

  override fun createPopupActionGroup(
    button: JComponent,
    dataContext: DataContext,
  ): DefaultActionGroup {
    val group = DefaultActionGroup()
    val manager = dataContext.getData(PreviewPaginationManager.Companion.KEY)

    manager?.let { m ->
      pageSizes.forEach { size ->
        group.add(
          object : AnAction(size.toString()) {
            override fun getActionUpdateThread(): ActionUpdateThread {
              return ActionUpdateThread.BGT
            }

            override fun actionPerformed(e: AnActionEvent) {
              m.pageSize = size
            }

            override fun update(e: AnActionEvent) {
              // TODO(b/417693700): Visually indicate the current selection
            }
          }
        )
      }
    }
    return group
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val manager = e.dataContext.getData(PreviewPaginationManager.Companion.KEY) ?: return

    val pageSize = manager.pageSize
    val selectedPage = manager.selectedPage // 0-indexed
    val totalElements = manager.getTotalElements() ?: return

    val from = selectedPage * pageSize + 1
    val maxInCurrentPage = from + pageSize - 1
    val to = minOf(maxInCurrentPage, totalElements)
    presentation.text =
      PreviewBundle.message("action.preview.pagination.page.range", from, to, totalElements)
    presentation.description =
      PreviewBundle.message("action.preview.pagination.page.size.description")
  }
}
