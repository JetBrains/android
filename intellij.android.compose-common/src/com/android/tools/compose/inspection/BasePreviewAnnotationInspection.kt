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
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Base interface that can be used for checking whether a given [KtAnnotationEntry] or
 * [KtImportDirective] is associated with a corresponding Preview tool annotation.
 */
interface PreviewAnnotationChecker {
  fun isPreview(importDirective: KtImportDirective): Boolean

  fun isPreview(annotation: KtAnnotationEntry): Boolean

  @RequiresReadLock
  fun isPreviewOrMultiPreview(annotation: KtAnnotationEntry): Boolean
}

/**
 * Base class for inspection that depend on methods and annotation classes annotated with
 * `@Preview`, or with a MultiPreview.
 */
abstract class BasePreviewAnnotationInspection(
  private val groupDisplayName: String,
  previewAnnotationChecker: PreviewAnnotationChecker,
) : AbstractKotlinInspection(), PreviewAnnotationChecker by previewAnnotationChecker {
  /**
   * Will be true if the inspected file imports the `@Preview` annotation. This is used as a
   * shortcut to avoid analyzing all kotlin files
   */
  var isPreviewFile: Boolean = false
  /**
   * Will be true if the inspected file imports the `@Composable` annotation. This is used as a
   * shortcut to avoid analyzing all kotlin files
   */
  var isComposableFile: Boolean = false

  /**
   * Called for every `@Preview` and MultiPreview annotation, that is annotating a function.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param function The function that was annotated with `@Preview` or with a MultiPreview
   * @param previewAnnotation The `@Preview` or MultiPreview annotation
   */
  abstract fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  )

  /**
   * Called for every `@Preview` and MultiPreview annotation, that is annotating an annotation
   * class.
   *
   * @param holder A [ProblemsHolder] user to report problems
   * @param annotationClass The annotation class that was annotated with `@Preview` or with a
   *   MultiPreview
   * @param previewAnnotation The `@Preview` or MultiPreview annotation
   */
  abstract fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  )

  final override fun buildVisitor(
    holder: ProblemsHolder,
    isOnTheFly: Boolean,
    session: LocalInspectionToolSession,
  ): PsiElementVisitor =
    if (session.file.androidFacet != null || ApplicationManager.getApplication().isUnitTestMode) {
      object : KtVisitorVoid() {
        override fun visitImportDirective(importDirective: KtImportDirective) {
          super.visitImportDirective(importDirective)

          isPreviewFile = isPreviewFile || isPreview(importDirective)
          isComposableFile =
            isComposableFile ||
              COMPOSABLE_ANNOTATION_FQ_NAME == importDirective.importedFqName?.asString()
        }

        override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
          super.visitAnnotationEntry(annotationEntry)

          isPreviewFile = isPreviewFile || isPreview(annotationEntry)
          isComposableFile =
            isComposableFile || annotationEntry.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
          super.visitNamedFunction(function)

          if (!isPreviewFile && !isComposableFile) {
            return
          }

          function.annotationEntries.forEach {
            if (isPreviewOrMultiPreview(it)) {
              visitPreviewAnnotation(holder, function, it)
            }
          }
        }

        override fun visitClass(klass: KtClass) {
          super.visitClass(klass)

          if (!klass.isAnnotation()) return

          klass.annotationEntries.forEach {
            if (isPreviewOrMultiPreview(it)) {
              visitPreviewAnnotation(holder, klass, it)
            }
          }
        }
      }
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  final override fun getGroupDisplayName(): String {
    return groupDisplayName
  }
}
