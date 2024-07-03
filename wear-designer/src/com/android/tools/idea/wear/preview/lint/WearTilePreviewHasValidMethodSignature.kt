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

import com.android.SdkConstants
import com.android.tools.idea.wear.preview.TILE_PREVIEW_DATA_FQ_NAME
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.hasTilePreviewAnnotation
import com.android.tools.idea.wear.preview.isMethodWithTilePreviewSignature
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiParameterList
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.uast.UMethod

/**
 * Inspection that checks that a method annotated with a Tile @Preview has the correct signature.
 *
 * @see [isMethodWithTilePreviewSignature] for details on what the signature should be.
 */
class WearTilePreviewHasValidMethodSignature : WearTilePreviewInspectionBase() {
  override fun checkMethod(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    if (!method.hasTilePreviewAnnotation()) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    if (method.sourcePsi.isMethodWithTilePreviewSignature()) {
      return super.checkMethod(method, manager, isOnTheFly)
    }

    return listOfNotNull(
        invalidReturnTypeError(method, manager, isOnTheFly),
        invalidParametersError(method, manager, isOnTheFly),
      )
      .toTypedArray()
  }

  override fun getStaticDescription() = message("inspection.invalid.signature")

  private fun invalidParametersError(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): ProblemDescriptor? {

    if (method.uastParameters.isEmpty()) {
      return null
    }

    val hasSingleContextParameter =
      method.uastParameters.size == 1 &&
        method.uastParameters.single().typeReference?.getQualifiedName() ==
          SdkConstants.CLASS_CONTEXT
    if (hasSingleContextParameter) {
      return null
    }

    val parameterList =
      method.sourcePsi?.let {
        it.getChildOfType<KtParameterList>() ?: it.getChildOfType<PsiParameterList>()
      } ?: return null

    return manager.createProblemDescriptor(
      parameterList,
      message("inspection.invalid.parameters"),
      isOnTheFly,
      LocalQuickFix.EMPTY_ARRAY,
      ProblemHighlightType.ERROR,
    )
  }

  private fun invalidReturnTypeError(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): ProblemDescriptor? {
    if (method.returnType?.equalsToText(TILE_PREVIEW_DATA_FQ_NAME) == true) {
      return null
    }

    return manager.createProblemDescriptor(
      method.uastAnchor?.sourcePsi ?: return null,
      message("inspection.invalid.return.type"),
      isOnTheFly,
      LocalQuickFix.EMPTY_ARRAY,
      ProblemHighlightType.ERROR,
    )
  }
}
