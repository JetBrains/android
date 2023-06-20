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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.util.ImportDescriptorResult
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * [PsiCallParameterPropertyItem] for @Preview parameters that can take an Enum from the project.
 * Can assign a fully qualified class to the value. While trying to import the class in to the
 * parameter's file.
 */
internal class ClassPsiCallParameter(
  project: Project,
  model: PsiCallPropertiesModel,
  resolvedCall: ResolvedCall<*>,
  descriptor: ValueParameterDescriptor,
  argumentExpression: KtExpression?,
  initialValue: String?
) :
  PsiCallParameterPropertyItem(
    project,
    model,
    resolvedCall,
    descriptor,
    argumentExpression,
    initialValue
  ) {

  /**
   * Imports the class defined by [fqClass], sets the property value from [newValue].
   *
   * [fqValue] is a fallback for [newValue] if the importing of [fqClass] fails. It's recommended
   * for that string to include fully qualified references of the desired imported class.
   *
   * [trackableValue] is the value reflected in usage tracking.
   *
   * E.g:
   *
   * fqClass -> android.content.res.Configuration
   *
   * newValue -> Configuration.UI_MODE_TYPE_NORMAL
   *
   * fqValue -> android.content.res.Configuration.UI_MODE_TYPE_NORMAL
   */
  fun importAndSetValue(
    fqClass: String,
    newValue: String,
    fqValue: String,
    trackableValue: PreviewPickerValue
  ) {
    val importResult = importClass(fqClass)

    if (importResult != null && importResult != ImportDescriptorResult.FAIL) {
      writeNewValue(newValue, true, trackableValue)
    } else {
      writeNewValue(fqValue, true, trackableValue)
    }
  }

  private fun importClass(fqClass: String) =
    model.ktFile.resolveImportReference(FqName(fqClass)).firstOrNull()?.let { importDescriptor ->
      WriteCommandAction.runWriteCommandAction<ImportDescriptorResult>(project) {
        ImportInsertHelper.getInstance(project).importDescriptor(model.ktFile, importDescriptor)
      }
    }
}
