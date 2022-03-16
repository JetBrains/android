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

import android.view.View
import android.widget.TextView
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.utils.HtmlBuilder
import java.awt.Rectangle

/**
 * Proportion of a view area allowed to be covered before emitting an issue.
 */
private const val OVERLAP_RATIO_THRESHOLD = 0.5

/**
 * [VisualLintAnalyzer] for issues where a view is covered by another sibling view.
 * Limit to covered [TextView] as they are the most likely to be wrongly covered by another view.
 */
object OverlapAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.OVERLAP

  override fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      findOverlapOfTextViewIssues(view, model, issues)
    }
    return issues
  }

  private fun findOverlapOfTextViewIssues(view: ViewInfo, model: NlModel, issueList: MutableList<VisualLintIssueContent>) {
    val children = view.children.filter { it.cookie != null && (it.viewObject as? View)?.visibility == View.VISIBLE }
    for (i in children.indices) {
      val firstView = children[i]
      if (firstView.viewObject !is TextView) {
        continue
      }
      for (j in children.indices) {
        val secondView = children[j]
        if (firstView == secondView) {
          continue
        }
        if (isPartiallyHidden(firstView, i, secondView, j, model)) {
          issueList.add(createIssueContent(firstView, secondView))
        }
      }
    }
  }

  private fun createIssueContent(firstView: ViewInfo, secondView: ViewInfo): VisualLintIssueContent {
    val summary = "${simpleName(firstView)} is covered by ${simpleName(secondView)}"
    val content = HtmlBuilder().add("The content of ${simpleName(firstView)} is partially hidden.")
      .newline()
      .add("This may pose a problem for the readability of the text it contains.")
    return VisualLintIssueContent(firstView, summary) { content }
  }

  /**
   * Given two view infos, and their respective indices in layout, figure out of [firstViewInfo] is being overlapped by [secondViewInfo]
   * and if the ratio of the area of the overlap region to the area of the [firstViewInfo] is bigger than [OVERLAP_RATIO_THRESHOLD].
   */
  private fun isPartiallyHidden(firstViewInfo: ViewInfo, i: Int, secondViewInfo: ViewInfo, j: Int, model: NlModel): Boolean {
    if (!isFirstViewUnderneath(firstViewInfo, i, secondViewInfo, j, model)) {
      return false
    }
    val firstWidth = firstViewInfo.right - firstViewInfo.left
    val firstHeight = firstViewInfo.bottom - firstViewInfo.top
    if (firstWidth == 0 || firstHeight == 0) {
      return false
    }
    val firstBounds = Rectangle(firstViewInfo.left, firstViewInfo.top, firstWidth, firstHeight)
    val secondBounds = Rectangle(secondViewInfo.left, secondViewInfo.top, secondViewInfo.right - secondViewInfo.left,
                                 secondViewInfo.bottom - secondViewInfo.top)
    val intersection = firstBounds.intersection(secondBounds)
    val coveredRatio = 1.0 * intersection.width * intersection.height / (firstWidth * firstHeight)
    return coveredRatio >= OVERLAP_RATIO_THRESHOLD
  }

  /**
   * Given two view infos, and their respective indices in layout, figure out if [firstViewInfo] is being drawn below [secondViewInfo].
   */
  private fun isFirstViewUnderneath(firstViewInfo: ViewInfo, firstViewIndex: Int, secondViewInfo: ViewInfo,
                                    secondViewIndex: Int, model: NlModel): Boolean {
    val comp1 = componentFromViewInfo(firstViewInfo, model)
    val comp2 = componentFromViewInfo(secondViewInfo, model)

    // Try to see if we can compare elevation attribute if it exists.
    if (comp1 != null && comp2 != null) {
      val elev1 = ConstraintComponentUtilities.getDpValue(comp1, comp1.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION))
      val elev2 = ConstraintComponentUtilities.getDpValue(comp2, comp2.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION))

      if (elev1 < elev2) {
        return true
      } else if (elev1 > elev2) {
        return false
      }
      // If they're the same, leave it to the index to resolve overlapping logic.
    }

    // else rely on index.
    return firstViewIndex < secondViewIndex
  }
}