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

import com.android.tools.idea.preview.annotations.findAllAnnotationsInGraph
import com.android.tools.idea.preview.quickfixes.ReplacePreviewAnnotationFix
import com.android.tools.idea.wear.preview.TILE_PREVIEW_ANNOTATION_FQ_NAME
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.isMethodWithTilePreviewSignature
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod

/**
 * Inspection that checks that any method with a Tile Preview signature is annotated with the tile
 * `@Preview` annotation, and not a `@Preview` annotation from a different package.
 */
class WearTilePreviewMethodIsAnnotatedWithTilePreviewAnnotation : WearTilePreviewInspectionBase() {

  override fun checkMethod(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    if (!method.sourcePsi.isMethodWithTilePreviewSignature()) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    return method.uAnnotations
      .mapNotNull { annotation ->
        val sourcePsi = annotation.sourcePsi ?: return@mapNotNull null
        when {
          annotation.qualifiedName.isPreviewFqnFromDifferentPackage() -> {
            manager.createProblemDescriptor(
              sourcePsi,
              message("inspection.preview.annotation.not.from.tile.package"),
              isOnTheFly,
              LocalQuickFix.notNullElements(
                ReplacePreviewAnnotationFix(
                  sourcePsi,
                  withAnnotationFqn = TILE_PREVIEW_ANNOTATION_FQ_NAME,
                )
              ),
              ProblemHighlightType.ERROR,
            )
          }
          annotation.isMultiPreviewAnnotationFromInvalidPackage() -> {
            manager.createProblemDescriptor(
              sourcePsi,
              message("inspection.preview.annotation.not.from.tile.package.multipreview"),
              isOnTheFly,
              LocalQuickFix.EMPTY_ARRAY,
              ProblemHighlightType.ERROR,
            )
          }
          else -> null
        }
      }
      .toTypedArray()
  }

  override fun getStaticDescription() =
    message("inspection.preview.annotation.not.from.tile.package")
}

private fun UAnnotation.isMultiPreviewAnnotationFromInvalidPackage() =
  findAllAnnotationsInGraph { it.qualifiedName.isPreviewFqnFromDifferentPackage() }.any()

private fun String?.isPreviewFqnFromDifferentPackage() =
  this?.endsWith(".Preview") == true && this != TILE_PREVIEW_ANNOTATION_FQ_NAME
