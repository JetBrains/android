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
package com.android.tools.idea.uibuilder.visual.visuallint.analyzers

import android.widget.EditText
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder

private const val TEXT_FIELD_MAX_DP_WIDTH = 488

/** [VisualLintAnalyzer] for issues where a text field is wider than the recommended 488dp. */
object TextFieldSizeAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.TEXT_FIELD_SIZE

  override val backgroundEnabled: Boolean
    get() = TextFieldSizeAnalyzerInspection.textFieldSizeBackground

  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel,
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      if (isWideTextField(view, model)) {
        issues.add(createIssueContent(view))
      }
    }
    return issues
  }

  private fun isWideTextField(view: ViewInfo, model: NlModel): Boolean {
    if (!checkIsClass(view, EditText::class.java)) {
      return false
    }
    val widthInDp = Coordinates.pxToDp(model, view.right - view.left)
    return widthInDp > TEXT_FIELD_MAX_DP_WIDTH
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val summary = "The text field ${nameWithId(view)} is too wide"
    val provider = { count: Int ->
      HtmlBuilder()
        .add(
          "The text field ${simpleName(view)} is wider than ${TEXT_FIELD_MAX_DP_WIDTH}dp in ${previewConfigurations(count)}."
        )
        .newline()
        .add(
          "Material Design recommends text fields to be no wider than ${TEXT_FIELD_MAX_DP_WIDTH}dp"
        )
    }
    return VisualLintIssueContent(view = view, message = summary, descriptionProvider = provider)
  }
}

class TextFieldSizeAnalyzerInspection :
  VisualLintInspection(VisualLintErrorType.TEXT_FIELD_SIZE, "textFieldSizeBackground") {
  companion object {
    var textFieldSizeBackground = true
  }
}
