/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.base.property

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.pickers.base.editingsupport.PsiEditingSupport
import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.preview.model.CurrentDeviceKey
import com.android.tools.idea.kotlin.tryEvaluateConstantAsText
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.SlowOperations
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.core.deleteElementAndCleanParent
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

private const val WRITE_COMMAND = "Psi Parameter Modification"

/**
 * A [PsiPropertyItem] for a named parameter.
 *
 * @param project the [Project] the PSI belongs to.
 * @param model the [PsiCallPropertiesModel] managing this property.
 * @param resolvedCall the parent [ResolvedCall] that contains this parameter.
 * @param descriptor the [ValueParameterDescriptor] of this parameter, containing the parameter
 *   metadata.
 * @param argumentExpression the initial [KtExpression] for the argument when this parameter was
 *   initialized.
 * @param defaultValue the default value string for the parameter, this is the value that the
 *   parameter takes when it does not have a
 *
 * ```
 *          user-assigned value
 * @param validation
 * ```
 *
 * function used for input validation
 */
internal open class PsiCallParameterPropertyItem(
  protected val project: Project,
  protected val model: PsiCallPropertiesModel,
  private val addNewArgumentToResolvedCall: (KtValueArgument, KtPsiFactory) -> KtValueArgument?,
  private val parameterName: Name,
  private val parameterTypeNameIfStandard: Name?,
  protected var argumentExpression: KtExpression?,
  override val defaultValue: String?,
  validation: EditingValidation = { EDITOR_NO_ERROR },
) : PsiPropertyItem {

  override var name: String
    get() = parameterName.identifier
    // We do not support editing property names.
    set(_) {}

  override val editingSupport: EditingSupport = PsiEditingSupport(validation)

  @OptIn(KaAllowAnalysisOnEdt::class)
  override var value: String?
    get() =
      SlowOperations.allowSlowOperations(
        ThrowableComputable {
          if (KotlinPluginModeProvider.isK2Mode()) {
            allowAnalysisOnEdt {
              argumentExpression?.let { analyze(it) { it.tryEvaluateConstantAsText(this) } }
            }
          } else {
            argumentExpression?.tryEvaluateConstantAsText()
          }
        }
      )
    set(value) {
      val newValue = value?.trim()?.nullize()
      val trackable =
        if (newValue == null) PreviewPickerValue.CLEARED
        else PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
      if (newValue != this.value) {
        writeNewValue(newValue, false, trackable)
      }
    }

  /**
   * Writes the [newValue] to the property's PsiElement, wrapped in double quotation marks when the
   * property's type is String, unless [writeAsIs] is True, in which case it will always be written
   * as it is.
   *
   * [trackableValue] should be an option that bests represents [newValue]. Use
   * [PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED] if none of the options matches the meaning of
   * the value, or [PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE] if the assigned value is
   * unexpected.
   */
  fun writeNewValue(newValue: String?, writeAsIs: Boolean, trackableValue: PreviewPickerValue) {
    model.tracker.registerModification(name, trackableValue, CurrentDeviceKey.getData(model))
    if (newValue == null) {
      deleteParameter()
    } else {
      val parameterString =
        if (!writeAsIs && parameterTypeNameIfStandard == Name.identifier("String")) {
          "${parameterName.asString()} = \"$newValue\""
        } else {
          "${parameterName.asString()} = $newValue"
        }
      writeParameter(parameterString)
    }
    model.firePropertyValuesChanged()
  }

  private fun deleteParameter() = runModification {
    argumentExpression?.parent?.deleteElementAndCleanParent()
    argumentExpression = null
  }

  private fun writeParameter(parameterString: String) = runModification {
    var newValueArgument = model.psiFactory.createArgument(parameterString)
    val currentArgumentExpression = argumentExpression

    if (currentArgumentExpression != null) {
      newValueArgument =
        currentArgumentExpression.parent.replace(newValueArgument) as KtValueArgument
    } else {
      addNewArgumentToResolvedCall(newValueArgument, model.psiFactory)?.let {
        newValueArgument = it
      }
    }
    argumentExpression = newValueArgument.getArgumentExpression()
    argumentExpression?.parent?.let { CodeStyleManager.getInstance(it.project).reformat(it) }
  }

  private fun runModification(invoke: () -> Unit) =
    WriteCommandAction.runWriteCommandAction(project, WRITE_COMMAND, null, invoke, model.ktFile)
}
