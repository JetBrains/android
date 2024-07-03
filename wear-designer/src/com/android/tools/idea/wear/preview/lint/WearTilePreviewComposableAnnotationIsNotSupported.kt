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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.hasTilePreviewAnnotation
import com.android.tools.idea.wear.preview.isMethodWithTilePreviewSignature
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.uast.UMethod

const val COMPOSABLE_ANNOTATION_FQN = "androidx.compose.runtime.Composable"

/**
 * Inspection that checks that a user is not using the @Composable annotation on a method with a
 * Tile preview signature.
 */
class WearTilePreviewComposableAnnotationIsNotSupported : WearTilePreviewInspectionBase() {
  override fun checkMethod(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    if (
      !method.sourcePsi.isMethodWithTilePreviewSignature() || !method.hasTilePreviewAnnotation()
    ) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    val composableAnnotation =
      method.uAnnotations.find { it.qualifiedName == COMPOSABLE_ANNOTATION_FQN }?.sourcePsi

    if (composableAnnotation == null) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    return arrayOf(
      manager.createProblemDescriptor(
        composableAnnotation,
        message("inspection.preview.annotation.composable.not.supported"),
        isOnTheFly,
        LocalQuickFix.EMPTY_ARRAY,
        ProblemHighlightType.ERROR,
      )
    )
  }

  override fun getStaticDescription() =
    message("inspection.preview.annotation.composable.not.supported")
}
