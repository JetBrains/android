/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.gradle.util.findCatalogKey
import com.android.tools.idea.gradle.util.findVersionCatalog
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.suppressHighlight
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.unsuppressHighlight
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

// This annotator is to substitute "Unresolved Reference" of KTS files
// Kts mechanism is linked to synthetics that are generated during sync
// So it gives false positive error if user update catalog and updating Kts reference.
class KtsCatalogAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is KtElement || !element.containingFile.name.endsWith("gradle.kts")) return

    if (element is KtDotQualifiedExpression && element.isEndOfExpression()) {
      val file = findVersionCatalog(element.text, element.project) ?: return
      val tomlElement = findCatalogKey(file, element.text.substringAfter("."))
      if (tomlElement != null)
      // recognized expression as reference to catalog dependency
        element.markChildrenAsSuppressHighlight()
      else {
        holder
          .newAnnotation(
            HighlightSeverity.ERROR,
            "Unresolved reference to version catalog",
          )
          .create()
        element.markChildrenAsUnsuppressHighlight()
      }
    }
  }

  private fun KtDotQualifiedExpression.isEndOfExpression() =
    (this.parent !is KtDotQualifiedExpression || this.parent.children.lastOrNull() !is KtNameReferenceExpression) &&
    this.hasOnlyNameReferences()

  private fun KtDotQualifiedExpression.hasOnlyNameReferences(): Boolean =
    this.children.all {
      when (it) {
        is KtNameReferenceExpression -> true
        is KtDotQualifiedExpression -> it.hasOnlyNameReferences()
        else -> false
      }
    }

  private fun KtDotQualifiedExpression.markChildrenAsSuppressHighlight() {
    this.children.forEach {
      when (it) {
        is KtNameReferenceExpression -> it.suppressHighlight()
        is KtDotQualifiedExpression -> it.markChildrenAsSuppressHighlight()
        else -> Unit
      }
    }
  }

  private fun KtDotQualifiedExpression.markChildrenAsUnsuppressHighlight() {
    this.children.forEach {
      when (it) {
        is KtNameReferenceExpression -> it.unsuppressHighlight()
        is KtDotQualifiedExpression -> it.markChildrenAsUnsuppressHighlight()
        else -> Unit
      }
    }
  }
}
