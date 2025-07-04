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

import com.android.tools.idea.wear.dwf.WFFConstants
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

/**
 * An [Annotator] that applies highlighting based on the PSI structure, not just lexer tokens. The
 * lexer tokens are highlighted by [WFFExpressionSyntaxHighlighter].
 */
class WFFExpressionAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    when (element) {
      is WFFExpressionFunctionId -> annotateFunctionId(element, holder)
      is WFFExpressionDataSource -> annotateDataSource(element, holder)
      is WFFExpressionConfiguration -> annotateConfiguration(element, holder)
    }
  }

  private fun annotateDataSource(dataSource: WFFExpressionDataSource, holder: AnnotationHolder) {
    if (dataSource.id.text !in WFFConstants.DataSources.ALL) {
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message("wff.expression.annotator.unknown.datasource"),
        )
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .range(dataSource.id)
        .create()
    }
    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(dataSource.id)
      .textAttributes(WFFExpressionTextAttributes.DATA_SOURCE.key)
      .create()
  }

  private fun annotateFunctionId(functionId: WFFExpressionFunctionId, holder: AnnotationHolder) {
    if (functionId.id.text !in WFFConstants.Functions.ALL) {
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message("wff.expression.annotator.unknown.function"),
        )
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .range(functionId)
        .create()
    }
    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(functionId)
      .textAttributes(WFFExpressionTextAttributes.FUNCTION_ID.key)
      .create()
  }

  private fun annotateConfiguration(
    configuration: WFFExpressionConfiguration,
    holder: AnnotationHolder,
  ) {
    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(configuration.configurationId)
      .textAttributes(WFFExpressionTextAttributes.CONFIGURATION.key)
      .create()
  }
}
