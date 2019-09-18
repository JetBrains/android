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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

private const val INSPECTIONS_GROUP_NAME = "Compose Preview"

/**
 * Base class for inspection that depend on methods annotated with `@Preview`.
 */
abstract class BasePreviewAnnotationInspection : AbstractKotlinInspection() {
  /** Will be true if the inspected file imports the `@Preview` annotation. This is used as a shortcut to avoid analyzing all kotlin files */
  var isPreviewFile: Boolean = false

  override fun getGroupDisplayName() = INSPECTIONS_GROUP_NAME

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
                                             previewAnnotation: KtAnnotationEntry,
                                             functionAnnotations: Map<String, KtAnnotationEntry>)

  final override fun buildVisitor(holder: ProblemsHolder,
                                  isOnTheFly: Boolean,
                                  session: LocalInspectionToolSession) =
    if (StudioFlags.COMPOSE_PREVIEW.get() &&
        (session.file.androidFacet != null || ApplicationManager.getApplication().isUnitTestMode)) {
      object : KtVisitorVoid() {
        override fun visitImportDirective(importDirective: KtImportDirective) {
          super.visitImportDirective(importDirective)

          isPreviewFile = isPreviewFile || PREVIEW_ANNOTATION_FQN == importDirective.importedFqName?.asString()
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
          super.visitNamedFunction(function)

          if (!isPreviewFile) {
            return
          }

          val annotationNames = function.annotationEntries
            .filter { it.getQualifiedName() != null }
            .associateBy { it.getQualifiedName()!! }

          val previewAnnotation = annotationNames[PREVIEW_ANNOTATION_FQN] ?: return

          visitPreviewAnnotatedFunction(holder, function, previewAnnotation, annotationNames)
        }
      }
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }
}

/**
 * Inspection that checks that any function annotated with `@Preview` does not have parameters.
 */
class PreviewAnnotationInFunctionWithParametersInspection : BasePreviewAnnotationInspection() {
  override fun getDisplayName() = message("inspection.no.parameters.name")

  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry,
                                             functionAnnotations: Map<String, KtAnnotationEntry>) {
    if (function.valueParameters.isNotEmpty()) {
      holder.registerProblem(previewAnnotation.psiOrParent as PsiElement,
                             message("inspection.no.parameters.description"),
                             ProblemHighlightType.ERROR)
    }
  }
}

/**
* Inspection that checks that any function annotated with `@Preview` is also annotated with `@Composable`.
*/
class PreviewNeedsComposableAnnotationInspection : BasePreviewAnnotationInspection() {
  override fun getDisplayName() = message("inspection.no.composable.name")

  override fun visitPreviewAnnotatedFunction(holder: ProblemsHolder,
                                             function: KtNamedFunction,
                                             previewAnnotation: KtAnnotationEntry,
                                             functionAnnotations: Map<String, KtAnnotationEntry>) {
    if (!functionAnnotations.contains(COMPOSABLE_ANNOTATION_FQN)) {
      holder.registerProblem(previewAnnotation.psiOrParent as PsiElement,
                             message("inspection.no.composable.description"),
                             ProblemHighlightType.ERROR)
    }
  }
}