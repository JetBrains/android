/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.wear.dwf.WFFConstants.DataSources
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.idea.wear.dwf.dom.raw.configurations.UserConfigurationReference
import com.android.tools.idea.wear.dwf.dom.raw.findDataSourceDefinition
import com.android.tools.idea.wear.dwf.dom.raw.isUserConfiguration
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

/**
 * An [Annotator] that applies highlighting based on the PSI structure, not just lexer tokens. The
 * lexer tokens are highlighted by [WFFExpressionSyntaxHighlighter].
 */
class WFFExpressionAnnotator() : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (element) {
      is WFFExpressionFunctionId -> annotateFunctionId(element, holder)
      is WFFExpressionDataSource -> annotateDataSource(element, holder)
    }
  }

  private fun annotateDataSource(dataSource: WFFExpressionDataSource, holder: AnnotationHolder) {
    when {
      dataSource.isUserConfiguration() -> annotateConfiguration(dataSource, holder)
      else -> annotatePredefinedDataSource(dataSource, holder)
    }
  }

  private fun annotatePredefinedDataSource(
    dataSource: WFFExpressionDataSource,
    holder: AnnotationHolder,
  ) {
    annotateSymbol(
      holder,
      element = dataSource.id,
      textAttributes = WFFExpressionTextAttributes.DATA_SOURCE,
      // The data source can be a complication data source used under the wrong type. This will
      // be reported as an error by InvalidComplicationDataSourceLocationInspection
      isUnknown =
        dataSource.findDataSourceDefinition() == null &&
          DataSources.COMPLICATION_ALL.none { it.id == dataSource.id.text },
      unknownMessage = message("wff.expression.annotator.unknown.datasource"),
    )
  }

  private fun annotateFunctionId(functionId: WFFExpressionFunctionId, holder: AnnotationHolder) {
    annotateSymbol(
      holder,
      element = functionId,
      textAttributes = WFFExpressionTextAttributes.FUNCTION_ID,
      isUnknown = findFunction(functionId.text) == null,
      unknownMessage = message("wff.expression.annotator.unknown.function"),
    )
  }

  private fun annotateConfiguration(
    configuration: WFFExpressionDataSource,
    holder: AnnotationHolder,
  ) {
    annotateSymbol(
      holder,
      configuration.id,
      WFFExpressionTextAttributes.CONFIGURATION,
      isUnknown = configuration.userConfigurationReference?.resolve() == null,
      message("wff.expression.annotator.unknown.configuration"),
    )
  }

  private fun annotateSymbol(
    holder: AnnotationHolder,
    element: PsiElement,
    textAttributes: WFFExpressionTextAttributes,
    isUnknown: Boolean,
    unknownMessage: String,
  ) {
    if (isUnknown) {
      holder
        .newAnnotation(HighlightSeverity.ERROR, unknownMessage)
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .range(element)
        .create()
    }
    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(element)
      .textAttributes(textAttributes.key)
      .create()
  }

  private val WFFExpressionDataSource.userConfigurationReference
    get() =
      parentOfType<WFFExpressionLiteralExpr>(withSelf = true)
        ?.references
        ?.filterIsInstance<UserConfigurationReference>()
        ?.firstOrNull()
}
