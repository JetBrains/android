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
package com.android.tools.idea.compose.pickers.spring.model

import com.android.tools.idea.compose.pickers.base.enumsupport.EnumSupportValuesProvider
import com.android.tools.idea.compose.pickers.base.inspector.PsiPropertiesInspectorBuilder
import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.base.model.PsiPropertiesProvider
import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.common.enumsupport.PsiEnumProvider
import com.android.tools.idea.compose.pickers.common.inspector.PsiEditorProvider
import com.android.tools.idea.compose.pickers.common.inspector.PsiPropertyItemControlTypeProvider
import com.android.tools.idea.compose.pickers.common.property.FloatPsiCallParameter
import com.android.tools.idea.compose.pickers.common.tracking.NoOpTracker
import com.android.tools.idea.compose.pickers.preview.utils.addNewValueArgument
import com.android.tools.idea.compose.pickers.preview.utils.getArgumentForParameter
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

private const val PARAMETER_RATIO = "dampingRatio"
private const val PARAMETER_STIFFNESS = "stiffness"
private const val PARAMETER_THRESHOLD = "visibilityThreshold"

internal class SpringPickerPropertiesModel(
  project: Project,
  module: Module,
  file: KtFile,
  psiPropertiesProvider: PsiPropertiesProvider,
) :
  PsiCallPropertiesModel(
    project = project,
    module = module,
    ktFile = file,
    psiPropertiesProvider = psiPropertiesProvider,
    tracker = NoOpTracker
  ) {

  override val inspectorBuilder: PsiPropertiesInspectorBuilder =
    object : PsiPropertiesInspectorBuilder() {
      override val editorProvider: EditorProvider<PsiPropertyItem> =
        PsiEditorProvider(
          PsiEnumProvider(EnumSupportValuesProvider.EMPTY),
          SpringControlTypeProvider
        )
    }

  override fun getData(dataId: String): Any? = null
}

/**
 * [PsiPropertiesProvider] for the Preview annotation. Provides specific implementations for known
 * parameters of the annotation.
 */
internal class SpringPropertiesProvider(private val resolvedCall: ResolvedCall<*>) :
  PsiPropertiesProvider {
  private fun addNewValueArgument(
    newValueArgument: KtValueArgument,
    psiFactory: KtPsiFactory
  ): KtValueArgument = resolvedCall.addNewValueArgument(newValueArgument, psiFactory)

  override fun invoke(
    project: Project,
    model: PsiCallPropertiesModel
  ): Collection<PsiPropertyItem> = runReadAction {
    resolvedCall.valueArguments
      .toList()
      .sortedBy { (descriptor, _) -> descriptor.index }
      .map { (descriptor, resolved) ->
        val argumentExpression =
          (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression()
        val parameterName = descriptor.name
        val parameterTypeNameIfStandard = descriptor.type.nameIfStandardType
        when (parameterName.asString()) {
          PARAMETER_THRESHOLD,
          PARAMETER_RATIO,
          PARAMETER_STIFFNESS ->
            FloatPsiCallParameter(
              project,
              model,
              ::addNewValueArgument,
              parameterName,
              parameterTypeNameIfStandard,
              argumentExpression,
              null,
            )
          else ->
            PsiCallParameterPropertyItem(
              project,
              model,
              ::addNewValueArgument,
              parameterName,
              parameterTypeNameIfStandard,
              argumentExpression,
              null,
            )
        }
      }
  }
}

internal class SpringPropertiesProviderK2(private val callElement: KtCallElement) :
  PsiPropertiesProvider {
  private fun addNewValueArgument(
    newValueArgument: KtValueArgument,
    psiFactory: KtPsiFactory
  ): KtValueArgument = callElement.addNewValueArgument(newValueArgument, psiFactory)

  @OptIn(KaAllowAnalysisOnEdt::class)
  override fun invoke(
    project: Project,
    model: PsiCallPropertiesModel
  ): Collection<PsiPropertyItem> = runReadAction {
    allowAnalysisOnEdt {
      analyze(callElement) {
        val resolvedFunctionCall =
          callElement.resolveCall()?.singleFunctionCallOrNull() ?: return@analyze emptyList()
        val callableSymbol = resolvedFunctionCall.symbol
        callableSymbol.valueParameters.map { parameter ->
          val argument = getArgumentForParameter(resolvedFunctionCall, parameter)
          val parameterName = parameter.name
          val parameterTypeNameIfStandard = parameter.returnType.expandedSymbol?.name
          when (parameterName.asString()) {
            PARAMETER_THRESHOLD,
            PARAMETER_RATIO,
            PARAMETER_STIFFNESS ->
              FloatPsiCallParameter(
                project,
                model,
                ::addNewValueArgument,
                parameterName,
                parameterTypeNameIfStandard,
                argument,
                null,
              )
            else ->
              PsiCallParameterPropertyItem(
                project,
                model,
                ::addNewValueArgument,
                parameterName,
                parameterTypeNameIfStandard,
                argument,
                null,
              )
          }
        }
      }
    }
  }
}

private object SpringControlTypeProvider : PsiPropertyItemControlTypeProvider {
  override fun invoke(property: PsiPropertyItem): ControlType =
    when (property.name) {
      PARAMETER_RATIO,
      PARAMETER_STIFFNESS,
      PARAMETER_THRESHOLD -> ControlType.COMBO_BOX
      else -> ControlType.TEXT_EDITOR
    }
}
