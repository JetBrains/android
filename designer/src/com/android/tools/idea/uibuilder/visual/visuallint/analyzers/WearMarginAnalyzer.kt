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

import android.view.ViewGroup
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder

private const val MIN_ROUND_MARGIN_RATIO = 0.052
private const val MIN_RECT_MARGIN_RATIO = 0.025

/** [VisualLintAnalyzer] for issues where a view is too close to the side of a Wear OS device. */
object WearMarginAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.WEAR_MARGIN

  override val backgroundEnabled: Boolean
    get() = WearMarginAnalyzerInspection.wearMarginBackground

  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque<ViewWithParentBounds>()
    val configuration = model.configuration
    val orientation = configuration.deviceState?.orientation ?: return issues
    val width = configuration.device?.getScreenSize(orientation)?.width ?: return issues
    val isRound = configuration.device?.isScreenRound ?: false
    val minPercent = if (isRound) MIN_ROUND_MARGIN_RATIO else MIN_RECT_MARGIN_RATIO
    val minLeft = minPercent * width
    val maxRight = width - minLeft
    renderResult.rootViews.forEach { viewsToAnalyze.add(ViewWithParentBounds(it, 0, width)) }
    while (viewsToAnalyze.isNotEmpty()) {
      val viewWithParentBounds = viewsToAnalyze.removeLast()
      val absoluteParentLeft = viewWithParentBounds.absoluteParentLeft
      val absoluteParentRight = viewWithParentBounds.absoluteParentRight
      if (absoluteParentLeft >= minLeft && absoluteParentRight <= maxRight) {
        continue
      }
      val view = viewWithParentBounds.view
      val absoluteViewLeft = view.left + absoluteParentLeft
      val absoluteViewRight = view.right + absoluteParentLeft
      if (absoluteViewLeft < minLeft || absoluteViewRight > maxRight) {
        if (view.viewObject !is ViewGroup) {
          issues.add(createIssueContent(view))
        } else {
          view.children.forEach {
            viewsToAnalyze.add(ViewWithParentBounds(it, absoluteViewLeft, absoluteViewRight))
          }
        }
      }
    }
    return issues
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val summary = "The view ${nameWithId(view)} is too close to the side of the device"
    val provider = { count: Int ->
      HtmlBuilder()
        .add(
          "In ${previewConfigurations(count)}, the view ${simpleName(view)} is closer to the side of the device than the recommended amount."
        )
        .newline()
        .add(
          "It is recommended that, for Wear OS layouts, margins should be at least ${MIN_RECT_MARGIN_RATIO * 100}% for square devices," +
            " and ${MIN_ROUND_MARGIN_RATIO * 100}% for round devices."
        )
    }
    return VisualLintIssueContent(view = view, message = summary, descriptionProvider = provider)
  }
}

data class ViewWithParentBounds(
  val view: ViewInfo,
  val absoluteParentLeft: Int,
  val absoluteParentRight: Int
)

class WearMarginAnalyzerInspection :
  VisualLintInspection(VisualLintErrorType.WEAR_MARGIN, "wearMarginBackground") {
  companion object {
    var wearMarginBackground = true
  }
}
