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
package com.android.tools.idea.compose.preview.rename

import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.compose.preview.ComposePreviewAnnotationChecker
import com.android.tools.idea.compose.preview.message
import com.intellij.psi.PsiElement
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * An [AutomaticRenamerFactory] that renames @Preview functions when a @Composable function is
 * renamed.
 */
class ComposePreviewAutomaticRenamerFactory : AutomaticRenamerFactory {
  override fun isApplicable(element: PsiElement): Boolean {
    return element is KtNamedFunction && element.isComposableFunction()
  }

  override fun getOptionName(): String {
    return message("rename.previews.option.name")
  }

  override fun isEnabled(): Boolean {
    return true
  }

  override fun setEnabled(enabled: Boolean) {}

  override fun createRenamer(
    element: PsiElement,
    newName: String,
    usages: MutableCollection<UsageInfo>,
  ): AutomaticRenamer {
    return object : AutomaticRenamer() {
      init {
        (element as? KtNamedFunction)?.let { composableFunction ->
          composableFunction.name?.let { originalName ->
            val previewFunctions =
              ReferencesSearch.search(
                  composableFunction,
                  ProjectScope.getProjectScope(composableFunction.project),
                )
                .findAll()
                .mapNotNull { it.element.parentOfType<KtNamedFunction>() }
                .filter { function ->
                  function.annotationEntries.any {
                    ComposePreviewAnnotationChecker.isPreviewOrMultiPreview(it)
                  }
                }
            myElements.addAll(previewFunctions)
            suggestAllNames(originalName, newName)
          }
        }
      }

      override fun getDialogTitle(): String {
        return message("rename.previews.dialog.title")
      }

      override fun getDialogDescription(): String {
        return message("rename.previews.dialog.description")
      }

      override fun entityName(): String {
        return message("rename.previews.entity.name")
      }

      override fun isSelectedByDefault(): Boolean {
        return true
      }
    }
  }
}
