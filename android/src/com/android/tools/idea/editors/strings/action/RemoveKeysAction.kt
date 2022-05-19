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
package com.android.tools.idea.editors.strings.action

import com.android.tools.idea.editors.strings.StringResourceViewPanel
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.res.getItemTag
import com.intellij.icons.AllIcons
import com.intellij.ide.util.DeleteHandler
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.safeDelete.SafeDeleteDialog
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import java.util.Arrays
import kotlin.streams.toList

/** Action to remove string resource keys. */
class RemoveKeysAction : PanelAction(text = "Remove Keys", description = null, icon = AllIcons.General.Remove) {
  override fun doUpdate(e: AnActionEvent) = e.panel.table.selectedRowCount > 0

  override fun actionPerformed(e: AnActionEvent) {
    perform(e.requiredProject, e.panel)
  }

  fun perform(project: Project, panel: StringResourceViewPanel) {
    val table: StringResourceTable = panel.table
    val model = table.model
    val repository = model.repository

    val keys: List<XmlTag> =
      table.selectedModelRowIndices
        .flatMap {index -> repository.getItems(model.getKey(index))}
        .mapNotNull {item -> getItemTag(project, item)}

    if (keys.isEmpty()) return

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, keys, /* notifyOnFail= */ true)) return

    if (DumbService.getInstance(project).isDumb) {
      DeleteHandler.deletePsiElement(keys.toTypedArray(), project)
      panel.reloadData()
      return
    }

    var checkboxSelected = false
    val dialog =
        object : SafeDeleteDialog(project, keys.toTypedArray(), { checkboxSelected = true }) {
          init {
            title = RefactoringBundle.message(/* key= */ "delete.title")
          }

          override fun isDelete() = true
        }

    if (!dialog.showAndGet()) return

    if (checkboxSelected) {
      SafeDeleteProcessor.createInstance(
              project,
              panel::reloadData,
              keys.toTypedArray(),
              dialog.isSearchInComments,
              dialog.isSearchForTextOccurences,
              /* askForAccessors= */ true)
          .run()
    }
    else {
      DeleteHandler.deletePsiElement(keys.toTypedArray(), project, /* needConfirmation= */ false)
      panel.reloadData()
    }
  }
}
