/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.annotator

import com.android.tools.idea.compose.preview.getContainingComposableUMethod
import com.android.tools.idea.preview.util.device.check.DeviceSpecCheck
import com.android.tools.idea.preview.util.device.check.DeviceSpecCheck.hasIssues
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElement

/** Singleton that provides methods to verify the correctness of the Compose @Preview annotation. */
internal object PreviewAnnotationCheck {
  /**
   * Checks if a [KtAnnotationEntry] element that should correspond to a reference of
   * Compose @Preview annotation has any issues.
   *
   * @see DeviceSpecCheck.hasIssues
   */
  fun KtAnnotationEntry.hasIssues(): Boolean {
    if (!ApplicationManager.getApplication().isReadAccessAllowed) {
      return true
    }
    val annotation = this.toUElement() as? UAnnotation ?: return true
    if (!hasValidTarget(annotation)) {
      return true
    }
    return annotation.hasIssues()
  }

  /**
   * Takes a [KtAnnotationEntry] element that should correspond to a reference of Compose @Preview
   * annotation and returns a [ProblemDescriptor] if any issues are found.
   *
   * @see DeviceSpecCheck.checkAnnotation
   */
  fun checkAnnotation(
    annotationEntry: KtAnnotationEntry,
    inspectionManager: InspectionManager,
    isOnTheFly: Boolean,
  ): ProblemDescriptor? {
    if (!ApplicationManager.getApplication().isReadAccessAllowed) {
      return inspectionManager.createProblemDescriptor(
        annotationEntry,
        "No read access",
        isOnTheFly,
        LocalQuickFix.EMPTY_ARRAY,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      )
    }
    val annotation =
      annotationEntry.toUElement() as? UAnnotation
        ?: return inspectionManager.createProblemDescriptor(
          annotationEntry,
          "Can't get annotation UElement",
          isOnTheFly,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        )
    if (!hasValidTarget(annotation)) {
      return inspectionManager.createProblemDescriptor(
        annotationEntry,
        "Preview target must be a composable function or an annotation class",
        isOnTheFly,
        LocalQuickFix.EMPTY_ARRAY,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      )
    }
    return DeviceSpecCheck.checkAnnotation(annotation, inspectionManager, isOnTheFly)
  }
}

private fun hasValidTarget(annotation: UAnnotation) =
  annotation.getContainingComposableUMethod() != null ||
    (annotation.getContainingUClass()?.isAnnotationType == true)
