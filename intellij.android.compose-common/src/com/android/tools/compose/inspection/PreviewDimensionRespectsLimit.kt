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

import com.android.tools.idea.kotlin.evaluateConstant
import com.android.tools.idea.kotlin.findValueArgument
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Inspection that checks that `@Preview` width and height parameters doesn't go lower nor higher than
 * [minDimension] and [maxDimension] respectively.
 */
open class PreviewDimensionRespectsLimit(
  private val widthAnnotationParam: String,
  private val heightAnnotationParam: String,
  private val minDimension: Int,
  private val maxDimension: Int,
  private val description: String,
  groupDisplayName: String,
  previewAnnotationChecker: PreviewAnnotationChecker,
) : BasePreviewAnnotationInspection(groupDisplayName, previewAnnotationChecker) {
  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  ) {
    checkMaxWidthAndHeight(holder, previewAnnotation)
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  ) {
    checkMaxWidthAndHeight(holder, previewAnnotation)
  }

  private fun checkMaxWidthAndHeight(holder: ProblemsHolder, previewAnnotation: KtAnnotationEntry) {
    // If it's not a preview, it must be a MultiPreview, and MultiPreview parameters don't affect
    // the Previews
    if (!isPreview(previewAnnotation)) return

    var isWithinLimits = true
    var psiElement: PsiElement? = null
    listOf(widthAnnotationParam, heightAnnotationParam).forEach { param ->
      previewAnnotation.findValueArgument(param)?.let {
        if (it.exceedsLimits(minDimension, maxDimension)) {
          isWithinLimits = false
          psiElement = it.psiOrParent
        }
      }
    }
    if (!isWithinLimits) {
      holder.registerProblem(psiElement!!, description, ProblemHighlightType.WARNING)
    }
  }

  override fun getStaticDescription() = description
}

private fun KtValueArgument.exceedsLimits(lowerLimit: Int, upperLimit: Int): Boolean {
  val argumentExpression = getArgumentExpression() ?: return false
  val dimension = argumentExpression.evaluateConstant<Int>() ?: return false
  return dimension < lowerLimit || upperLimit < dimension
}
