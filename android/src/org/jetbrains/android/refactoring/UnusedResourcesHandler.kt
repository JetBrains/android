/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler

class UnusedResourcesHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    UnusedResourcesDialog(
      project,
      UnusedResourcesDialog.FilterAndDescription(
        UnusedResourcesProcessor.FileFilter.from(setOf(file)),
        "the refactoring is restricted to the resources in the currently open file",
      ),
    ).show()
  }

  @UiThread
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext) {
    UnusedResourcesDialog(
      project,
      if (elements.isEmpty()) {
        null
      } else {
        UnusedResourcesDialog.FilterAndDescription(
          UnusedResourcesProcessor.FileFilter.from (elements.toList()),
          "the refactoring is restricted to the resources in the currently selected files/directories",
        )
      },
    ).show()
  }
}
