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

import com.android.tools.compose.inspection.BasePreviewAnnotationInspection
import com.android.tools.idea.compose.preview.ComposePreviewAnnotationChecker
import com.android.tools.idea.compose.preview.composePreviewGroupDisplayName
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * LocalInspection for the Compose @Preview annotation.
 *
 * Outlines IDE-specific issues with the annotation's contents (i.e: the library has independent
 * Lint checks of its own).
 */
class PreviewPickerAnnotationInspection :
  BasePreviewAnnotationInspection(composePreviewGroupDisplayName, ComposePreviewAnnotationChecker) {

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    function: KtNamedFunction,
    previewAnnotation: KtAnnotationEntry,
  ) {
    runPreviewPickerChecks(holder, previewAnnotation)
  }

  override fun visitPreviewAnnotation(
    holder: ProblemsHolder,
    annotationClass: KtClass,
    previewAnnotation: KtAnnotationEntry,
  ) {
    runPreviewPickerChecks(holder, previewAnnotation)
  }

  private fun runPreviewPickerChecks(holder: ProblemsHolder, previewAnnotation: KtAnnotationEntry) {
    // If it's not a preview, it must be a MultiPreview, and MultiPreviews are not relevant for the
    // PreviewPicker
    if (!isPreview(previewAnnotation)) return

    if (previewAnnotation.getModuleSystem()?.isPreviewPickerEnabled() != true) return

    PreviewAnnotationCheck.checkAnnotation(previewAnnotation, holder.manager, holder.isOnTheFly)
      ?.let { holder.registerProblem(it) }
  }
}
