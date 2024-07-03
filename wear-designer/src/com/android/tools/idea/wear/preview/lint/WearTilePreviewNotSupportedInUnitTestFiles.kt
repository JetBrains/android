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
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.uast.UMethod

/**
 * Inspection that checks that functions annotated with `@Preview`, or with a MultiPreview, are not
 * in a unit test file.
 */
class WearTilePreviewNotSupportedInUnitTestFiles :
  WearTilePreviewInspectionBase(isUnitTestInspection = true) {

  override fun checkMethod(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    // We are only interested in methods annotated with the tile preview annotation
    if (!method.hasTilePreviewAnnotation()) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    return method.sourcePsi?.let { sourcePsi ->
      arrayOf(
        manager.createProblemDescriptor(
          sourcePsi,
          message("inspection.unit.test.files"),
          isOnTheFly,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.ERROR,
        )
      )
    }
  }

  override fun getStaticDescription() = message("inspection.unit.test.files")
}
