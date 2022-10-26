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
package com.android.tools.idea.editors.liveedit.ui

import com.android.tools.idea.editors.literals.EditState
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

class LiveEditActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project: Project? = editor.project
    return if (project == null ||
               project.isDefault) null else
      object : DefaultActionGroup(LiveEditAction(editor), Separator.create()) {
        override fun update(e: AnActionEvent) {
          val proj = e.project ?: return
          if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit) {
            e.presentation.isEnabledAndVisible = false
            return
          }
          val psiFile = PsiDocumentManager.getInstance(proj).getPsiFile(editor.document)
          if (!proj.isInitialized || psiFile == null || !psiFile.virtualFile.isKotlinFileType() || !editor.document.isWritable) {
            e.presentation.isEnabledAndVisible = false
            return
          }
          val editStatus = LiveEditService.getInstance(project).editStatus()
          e.presentation.isEnabledAndVisible = (editStatus.editState != EditState.DISABLED)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
          return ActionUpdateThread.EDT
        }
      }
  }
}