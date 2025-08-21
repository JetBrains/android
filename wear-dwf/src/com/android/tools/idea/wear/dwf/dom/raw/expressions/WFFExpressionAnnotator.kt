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

import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.wear.dwf.WFFConstants.DataSources
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.idea.wear.dwf.dom.raw.CurrentWFFVersionService
import com.android.tools.idea.wear.dwf.dom.raw.configurations.UserConfigurationReference
import com.android.tools.idea.wear.dwf.dom.raw.findDataSourceDefinition
import com.android.tools.idea.wear.dwf.dom.raw.isUserConfiguration
import com.android.tools.wear.wff.WFFVersion
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
    val wffVersion =
      element.getModuleSystem()?.module?.let { module ->
        CurrentWFFVersionService.getInstance().getCurrentWFFVersion(module)?.wffVersion
      }
    when (element) {
      is WFFExpressionFunctionId -> annotateFunctionId(wffVersion, element, holder)
      is WFFExpressionDataSource -> annotateDataSource(wffVersion, element, holder)
    }
  }

  private fun annotateDataSource(
    wffVersion: WFFVersion?,
    dataSource: WFFExpressionDataSource,
    holder: AnnotationHolder,
  ) {
    when {
      dataSource.isUserConfiguration() -> annotateConfiguration(dataSource, holder)
      else -> annotatePredefinedDataSource(wffVersion, dataSource, holder)
    }
  }

  private fun annotatePredefinedDataSource(
    wffVersion: WFFVersion?,
    dataSource: WFFExpressionDataSource,
    holder: AnnotationHolder,
  ) {
    val dataSourceDefinition = dataSource.findDataSourceDefinition()
    // The data source can be a complication data source used under the wrong type. This will
    // be reported as an error by InvalidComplicationDataSourceLocationInspection
    val isDataSourceUnknown =
      dataSourceDefinition == null &&
        DataSources.COMPLICATION_ALL.none { it.id == dataSource.id.text }
    if (isDataSourceUnknown) {
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message("wff.expression.annotator.unknown.datasource"),
        )
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .range(dataSource.id)
        .create()
    } else if (
      dataSourceDefinition != null &&
        wffVersion != null &&
        wffVersion < dataSourceDefinition.requiredVersion
    ) {
      // TODO(b/436560081): move this to a local inspection, this annotator should only highlight
      // unknown data sources
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message(
            "wff.expression.annotator.unavailable.datasource",
            dataSourceDefinition.requiredVersion.version,
          ),
        )
        .range(dataSource.id)
        .create()
    }
    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(dataSource.id)
      .textAttributes(WFFExpressionTextAttributes.DATA_SOURCE.key)
      .create()
  }

  private fun annotateFunctionId(
    wffVersion: WFFVersion?,
    functionId: WFFExpressionFunctionId,
    holder: AnnotationHolder,
  ) {
    val function = findFunction(functionId.text)
    if (function == null) {
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message("wff.expression.annotator.unknown.function"),
        )
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .range(functionId)
        .create()
    } else if (wffVersion != null && wffVersion < function.requiredVersion) {
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message("wff.expression.annotator.unavailable.function", function.requiredVersion.version),
        )
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
    configuration: WFFExpressionDataSource,
    holder: AnnotationHolder,
  ) {
    val reference =
      configuration
        .parentOfType<WFFExpressionLiteralExpr>(withSelf = true)
        ?.references
        ?.filterIsInstance<UserConfigurationReference>()
        ?.firstOrNull()
    if (reference?.resolve() == null) {
      holder
        .newAnnotation(
          HighlightSeverity.ERROR,
          message("wff.expression.annotator.unknown.configuration"),
        )
        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        .range(configuration.id)
        .create()
    }
    holder
      .newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(configuration.id)
      .textAttributes(WFFExpressionTextAttributes.CONFIGURATION.key)
      .create()
  }
}
