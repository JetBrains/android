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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction

/**
 * Migrate to Gradle version catalogs.
 *
 * TODO: Extend AndroidGradleBaseRefactoringAction instead??
 */
class MigrateToCatalogAction : BaseRefactoringAction() {
  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    presentation.isEnabledAndVisible =
      StudioFlags.MIGRATE_TO_VERSION_CATALOG_REFACTORING_ENABLED.get() &&
        MigrateToCatalogProcessor.applies(project)
  }

  override fun isEnabledOnDataContext(dataContext: DataContext) = true

  public override fun isAvailableInEditorOnly() = false

  override fun isAvailableForLanguage(language: Language) = true

  public override fun isEnabledOnElements(elements: Array<PsiElement>) = true

  public override fun getHandler(dataContext: DataContext): RefactoringActionHandler =
    MigrateToCatalogHandler()
}
