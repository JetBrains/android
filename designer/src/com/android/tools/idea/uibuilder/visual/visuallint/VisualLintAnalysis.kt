/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.AtfAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LocaleAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzer

enum class VisualLintErrorType(private val ignoredAttributeName: String) {
  TEXT_FIELD_SIZE("TextFieldSize"),
  BUTTON_SIZE("ButtonSize"),
  BOUNDS("Bounds"),
  BOTTOM_NAV("BottomNav"),
  BOTTOM_APP_BAR("BottomAppBar"),
  OVERLAP("Overlap"),
  LONG_TEXT("LongText"),
  ATF("AccessibilityTestFramework"),
  LOCALE_TEXT("LocaleText");

  /**
   * The values for tools:ignore attribute. This is used by suppression.
   */
  val ignoredAttributeValue: String
    get() = ATTRIBUTE_PREFIX + ignoredAttributeName

  fun toSuppressActionDescription(): String {
    return """Add ${SdkConstants.TOOLS_NS_NAME_PREFIX}${SdkConstants.ATTR_IGNORE}="$ignoredAttributeValue""""
  }

  companion object {
    private const val ATTRIBUTE_PREFIX = "VisualLint"

    fun getTypeByIgnoredAttribute(value: String): VisualLintErrorType? {
      return values().firstOrNull { it.ignoredAttributeValue == value }
    }
  }
}

private val basicAnalyzers = listOf(BoundsAnalyzer, BottomNavAnalyzer, BottomAppBarAnalyzer, TextFieldSizeAnalyzer,
                                    OverlapAnalyzer, LongTextAnalyzer, ButtonSizeAnalyzer)

/**
 * Collects in [issueProvider] all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
 */
fun analyzeAfterModelUpdate(result: RenderResult,
                            model: NlModel,
                            issueProvider: VisualLintIssueProvider,
                            baseConfigIssues: VisualLintBaseConfigIssues,
                            analyticsManager: VisualLintAnalyticsManager) {
  basicAnalyzers.forEach {
    val issues = it.analyze(result, model, analyticsManager)
    issueProvider.addAllIssues(it.type, issues)
  }
  LocaleAnalyzer(baseConfigIssues).let { issueProvider.addAllIssues(it.type, it.analyze(result, model, analyticsManager)) }
  if (StudioFlags.NELE_ATF_IN_VISUAL_LINT.get()) {
    AtfAnalyzer.analyze(result, model, issueProvider)
  }
}
