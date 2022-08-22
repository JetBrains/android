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
import com.android.tools.idea.compose.preview.PARAMETER_RATIO
import com.android.tools.idea.compose.preview.PARAMETER_STIFFNESS
import com.android.tools.idea.compose.preview.PARAMETER_THRESHOLD
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

internal class SpringPickerPropertiesModel(
  project: Project,
  module: Module,
  resolvedCall: ResolvedCall<*>,
) :
  PsiCallPropertiesModel(
    project = project,
    module = module,
    resolvedCall = resolvedCall,
    psiPropertiesProvider = SpringPropertiesProvider,
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
private object SpringPropertiesProvider : PsiPropertiesProvider {
  override fun invoke(
    project: Project,
    model: PsiCallPropertiesModel,
    resolvedCall: ResolvedCall<*>
  ): Collection<PsiPropertyItem> = runReadAction {
    resolvedCall.valueArguments.toList().sortedBy { (descriptor, _) -> descriptor.index }.map {
      (descriptor, resolved) ->
      val argumentExpression =
        (resolved as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression()
      when (descriptor.name.asString()) {
        PARAMETER_THRESHOLD, PARAMETER_RATIO, PARAMETER_STIFFNESS ->
          FloatPsiCallParameter(project, model, resolvedCall, descriptor, argumentExpression, null)
        else ->
          PsiCallParameterPropertyItem(
            project,
            model,
            resolvedCall,
            descriptor,
            argumentExpression,
            null
          )
      }
    }
  }
}

private object SpringControlTypeProvider : PsiPropertyItemControlTypeProvider {
  override fun invoke(property: PsiPropertyItem): ControlType =
    when (property.name) {
      PARAMETER_RATIO, PARAMETER_STIFFNESS, PARAMETER_THRESHOLD -> ControlType.COMBO_BOX
      else -> ControlType.TEXT_EDITOR
    }
}
