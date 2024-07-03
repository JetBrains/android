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
package com.android.tools.compose.inspection

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Inspection that checks that any function annotated with `@Preview`, or with a MultiPreview, is
 * also annotated with `@Composable`.
 */
open class PreviewNeedsComposableAnnotationInspection(
  private val description: String,
  groupDisplayName: String,
  previewAnnotationChecker: PreviewAnnotationChecker,
) : BasePreviewAnnotationInspection(groupDisplayName, previewAnnotationChecker) {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  ) {
    val nonComposable =
      function.annotationEntries.none { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) }
    if (nonComposable) {
      holder.registerProblem(
        previewAnnotation.psiOrParent as PsiElement,
        description,
        ProblemHighlightType.ERROR,
      )
    }
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  ) {
    // This inspection only applies for functions, not for Annotation classes
    return
  }

  override fun getStaticDescription() = description
}