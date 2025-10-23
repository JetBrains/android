/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.property

import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * [PsiCallParameterPropertyItem] for @Preview parameters that can take an Enum from the project.
 * Can assign a fully qualified class to the value. While trying to import the class in to the
 * parameter's file.
 */
internal class ClassPsiCallParameter(
  project: Project,
  model: PsiCallPropertiesModel,
  addNewArgumentToResolvedCall: (KtValueArgument, KtPsiFactory) -> KtValueArgument?,
  parameterName: Name,
  parameterTypeNameIfStandard: Name?,
  argumentExpression: KtExpression?,
  initialValue: String?,
) :
  PsiCallParameterPropertyItem(
    project,
    model,
    addNewArgumentToResolvedCall,
    parameterName,
    parameterTypeNameIfStandard,
    argumentExpression,
    initialValue,
  ) {

  /**
   * Sets the property value and attempts to shorten the reference.
   *
   * This method updates the property with a fully qualified value (`fqValue`), and then triggers
   * the IDE's `ShortenReferencesFacility` to automatically add the necessary import statements and
   * simplify the reference in the code.
   *
   * For example, if you have:
   * ```
   * @Preview(uiMode = 0)
   * ```
   *
   * Calling this function with:
   * - `fqValue` = `"android.content.res.Configuration.UI_MODE_TYPE_NORMAL"`
   *
   * Will result in the following code, with the import for `Configuration` being added
   * automatically:
   * ```
   * import android.content.res.Configuration
   *
   * @Preview(uiMode = Configuration.UI_MODE_TYPE_NORMAL)
   * ```
   *
   * @param fqValue The fully qualified string representation of the value to be set.
   * @param trackableValue A value for usage tracking.
   */
  fun importAndSetValue(fqValue: String, trackableValue: PreviewPickerValue) {
    writeNewValue(fqValue, true, trackableValue)
    argumentExpression?.let { expression ->
      runWriteCommandAction<PsiElement>(project) {
        ShortenReferencesFacility.getInstance().shorten(expression)
      }
    }
  }
}
