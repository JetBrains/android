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
package com.android.tools.idea.glance.preview

import com.android.tools.compose.inspection.PreviewAnnotationChecker
import com.android.tools.compose.inspection.PreviewMustBeTopLevelFunction
import com.android.tools.compose.inspection.PreviewNeedsComposableAnnotationInspection
import com.android.tools.compose.inspection.PreviewNotSupportedInUnitTestFiles
import com.android.tools.idea.glance.preview.GlancePreviewBundle.message
import com.android.tools.idea.kotlin.fqNameMatches
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

private val glancePreviewGroupDisplayName = message("inspection.group.name")

private object GlancePreviewAnnotationChecker : PreviewAnnotationChecker {
  override fun isPreview(importDirective: KtImportDirective) =
    GLANCE_PREVIEW_ANNOTATION_FQN == importDirective.importedFqName?.asString()

  override fun isPreview(annotation: KtAnnotationEntry) =
    annotation.fqNameMatches(GLANCE_PREVIEW_ANNOTATION_FQN)

  override fun isPreviewOrMultiPreview(annotation: KtAnnotationEntry) =
    isPreview(annotation) ||
      (annotation.toUElement() as? UAnnotation)?.let { isMultiPreviewAnnotation(it) } == true
}

class GlancePreviewNeedsComposableAnnotationInspection :
  PreviewNeedsComposableAnnotationInspection(
    message("inspection.no.composable.description"),
    glancePreviewGroupDisplayName,
    GlancePreviewAnnotationChecker,
  )

class GlancePreviewMustBeTopLevelFunction :
  PreviewMustBeTopLevelFunction(
    message("inspection.top.level.function"),
    glancePreviewGroupDisplayName,
    GlancePreviewAnnotationChecker,
  )

class GlancePreviewNotSupportedInUnitTestFiles :
  PreviewNotSupportedInUnitTestFiles(
    message("inspection.unit.test.files"),
    glancePreviewGroupDisplayName,
    GlancePreviewAnnotationChecker,
  )
