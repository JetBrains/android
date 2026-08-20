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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * [PsiCallParameterPropertyItem] for @Preview parameters that can take an Enum from the project. Can assign a fully qualified class to the
 * value. While trying to import the class in to the parameter's file.
 */
internal class ClassPsiCallParameter(
  project: Project,
  model: PsiCallPropertiesModel,
  addNewArgumentToResolvedCall: (KtValueArgument, KtPsiFactory) -> KtValueArgument?,
  parameterName: Name,
  parameterTypeNameIfStandard: Name?,
  argumentExpression: KtExpression?,
  defaultValue: String?,
  initialValue: String? = null,
) :
  PsiCallParameterPropertyItem(
    project,
    model,
    addNewArgumentToResolvedCall,
    parameterName,
    parameterTypeNameIfStandard,
    argumentExpression,
    defaultValue,
    initialValue = initialValue,
  ) {

  /**
   * Sets the property value and attempts to shorten the reference.
   *
   * This method updates the property with a fully qualified value (`fqValue`), and then triggers the IDE's `ShortenReferencesFacility` to
   * automatically add the necessary import statements and simplify the reference in the code.
   *
   * For example, if you have:
   * ```
   * @Preview(uiMode = 0)
   * ```
   *
   * Calling this function with:
   * - `fqValue` = `"android.content.res.Configuration.UI_MODE_TYPE_NORMAL"`
   *
   * Will result in the following code, with the import for `Configuration` being added automatically:
   * ```
   * import android.content.res.Configuration
   *
   * @Preview(uiMode = Configuration.UI_MODE_TYPE_NORMAL)
   * ```
   *
   * @param fqValue The fully qualified string representation of the value to be set.
   * @param trackableValue A value for usage tracking.
   * @param expectedValue The expected display value after shortening (e.g. `"Configuration.UI_MODE_TYPE_NORMAL"`). Passing this populates
   *   `cachedValue` synchronously in [writeNewValue] after reference shortening, preventing UI combo box flickering due to async PSI
   *   analysis (b/538471520).
   */
  @UiThread
  fun importAndSetValue(fqValue: String, trackableValue: PreviewPickerValue, expectedValue: String? = null) {
    writeNewValue(fqValue, true, trackableValue, expectedValue) {
      val argElement = argumentExpression?.element
      if (argElement != null) {
        val parentValueArgument = argElement.parent as? KtValueArgument
        val fullyQualifiedExpressions =
          argElement.collectDescendantsOfType<KtDotQualifiedExpression> { dotExpr ->
            dotExpr.parent !is KtDotQualifiedExpression && dotExpr.receiverExpression.text.contains('.')
          }
        if (fullyQualifiedExpressions.isNotEmpty()) {
          fullyQualifiedExpressions.forEach { ShortenReferencesFacility.getInstance().shorten(it) }
        } else if (argElement is KtDotQualifiedExpression && argElement.receiverExpression.text.contains('.')) {
          ShortenReferencesFacility.getInstance().shorten(argElement)
        }
        // Reference shortening can replace the underlying PSI element. Re-query the argument expression from the parent
        // KtValueArgument and update argumentExpression with a new SmartPsiElementPointer.
        val shortenedExpression = parentValueArgument?.getArgumentExpression()
        if (shortenedExpression != null) {
          argumentExpression = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(shortenedExpression)
        }
      }
    }
  }
}
