/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.util.*
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.findValueArgument
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.android.compose.COMPOSABLE_FQ_NAMES
import org.jetbrains.android.compose.PREVIEW_ANNOTATION_FQNS
import org.jetbrains.android.compose.PREVIEW_PARAMETER_FQNS
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*

/**
 * Base class for inspection that depend on methods annotated with `@Preview`.
 */
abstract class BasePreviewAnnotationInspection : AbstractKotlinInspection() {
  /** Will be true if the inspected file imports the `@Preview` annotation. This is used as a shortcut to avoid analyzing all kotlin files */
  var isPreviewFile: Boolean = false

  /**
   * Called for every function annotated with `@Preview` annotation.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param function The function that was annotated with `@Preview`
   * @param previewAnnotation The `@Preview` annotation
   * @param functionAnnotations All the annotations of the method indexed by the FQN, including the `@Preview` annotation.
   */
  abstract fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry)

  final override fun buildVisitor(holder: ProblemsHolder,
                                  isOnTheFly: Boolean,
                                  session: LocalInspectionToolSession) =
    if (StudioFlags.COMPOSE_PREVIEW.get() &&
        (session.file.androidFacet != null || ApplicationManager.getApplication().isUnitTestMode)) {
      object : KtVisitorVoid() {
        override fun visitImportDirective(importDirective: KtImportDirective) {
          super.visitImportDirective(importDirective)

          isPreviewFile = isPreviewFile || PREVIEW_ANNOTATION_FQNS.contains(importDirective.importedFqName?.asString())
        }

        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
          super.visitAnnotationEntry(annotationEntry)

          isPreviewFile = isPreviewFile || annotationEntry.fqNameMatches(PREVIEW_ANNOTATION_FQNS)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
          super.visitNamedFunction(function)

          if (!isPreviewFile) {
            return
          }

          val previewAnnotation = function.annotationEntries.firstOrNull { it.fqNameMatches(PREVIEW_ANNOTATION_FQNS) } ?: return
          visitPreviewAnnotatedFunction(holder, function, previewAnnotation)
        }
      }
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }
}

/**
 * Returns whether the [KtParameter] can be used in the preview. This will return true if the parameter
 * has a default value or a value provider.
 */
private fun KtParameter.isAcceptableForPreview(): Boolean =
  hasDefaultValue() ||
  // We also accept parameters with the @PreviewParameter annotation
  annotationEntries.any { it.fqNameMatches(PREVIEW_PARAMETER_FQNS) }

/**
 * Inspection that checks that any function annotated with `@Preview` does not have parameters.
 */
class PreviewAnnotationInFunctionWithParametersInspection : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry) {
    if (function.valueParameters.any { !it.isAcceptableForPreview() }) {
      holder.registerProblem(previewAnnotation.psiOrParent as PsiElement,
                             message("inspection.no.parameters.or.provider.description"),
                             ProblemHighlightType.ERROR)
    }
  }
}

/**
 * Inspection that checks that any `@Preview` function only has at most one `@PreviewParameter`.
 */
class PreviewMultipleParameterProvidersInspection : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry) {
    // Find the second PreviewParameter annotation if any
    val secondPreviewParameter = function.valueParameters.mapNotNull {
      it.annotationEntries.firstOrNull { annotation -> annotation.fqNameMatches(PREVIEW_PARAMETER_FQNS) }
    }.drop(1).firstOrNull() ?: return

    // Flag the second annotation as the error
    holder.registerProblem(secondPreviewParameter as PsiElement,
                           message("inspection.no.multiple.preview.provider.description"),
                           ProblemHighlightType.ERROR)
  }
}

/**
 * Inspection that checks that any function annotated with `@Preview` is also annotated with `@Composable`.
 */
class PreviewNeedsComposableAnnotationInspection : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry) {
    val nonComposable = function.annotationEntries.none { it.fqNameMatches(COMPOSABLE_FQ_NAMES) }
    if (nonComposable) {
      holder.registerProblem(previewAnnotation.psiOrParent as PsiElement,
                             message("inspection.no.composable.description"),
                             ProblemHighlightType.ERROR)
    }
  }
}

/**
 * Inspection that checks that any `@Preview` is a top level method.
 * This is to avoid `@Preview` methods to be instance methods of classes that we can not instantiate.
 */
class PreviewMustBeTopLevelFunction : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry) {
    if (function.isValidPreviewLocation()) return

    holder.registerProblem(previewAnnotation.psiOrParent as PsiElement,
                           message("inspection.top.level.function"),
                           ProblemHighlightType.ERROR)
  }
}

/**
 * Inspection that checks that `@Preview` width parameter doesn't go higher than [MAX_WIDTH],
 * and the height parameter doesn't go higher than [MAX_HEIGHT].
 */
class PreviewDimensionRespectsLimit : BasePreviewAnnotationInspection() {
  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry) {
    previewAnnotation.findValueArgument(WIDTH_PARAMETER)?.let {
      if (it.exceedsLimit(MAX_WIDTH)) {
        holder.registerProblem(it.psiOrParent as PsiElement,
                               message("inspection.width.limit.description", MAX_WIDTH),
                               ProblemHighlightType.WARNING)
      }
    }

    previewAnnotation.findValueArgument(HEIGHT_PARAMETER)?.let {
      if (it.exceedsLimit(MAX_HEIGHT)) {
        holder.registerProblem(it.psiOrParent as PsiElement,
                               message("inspection.height.limit.description", MAX_HEIGHT),
                               ProblemHighlightType.WARNING)
      }
    }

  }
}

private fun KtValueArgument.exceedsLimit(limit: Int): Boolean {
  (getArgumentExpression() as? PsiElement)?.node?.text?.toIntOrNull()?.let { return it > limit }
  return false
}