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
package com.android.tools.idea.refactoring.catalog

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler

class MigrateToCatalogHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    invokeWithDialog(project)
  }

  @UiThread
  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext) {
    invokeWithDialog(project)
  }
}

private fun invokeWithDialog(project: Project) {
  val processor =
    MigrateToCatalogProcessor(project).apply {
      syncProject = true
      openCatalogFile = true
    }
  MigrateToCatalogDialog(project, processor).show()
}
