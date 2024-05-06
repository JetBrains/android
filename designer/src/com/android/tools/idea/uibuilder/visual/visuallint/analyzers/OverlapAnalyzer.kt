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

import android.graphics.RectF
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder
import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Proportion of a view area allowed to be covered before emitting an issue. */
private const val OVERLAP_RATIO_THRESHOLD = 0.5

/**
 * [VisualLintAnalyzer] for issues where a view is covered by another sibling view. Limit to covered
 * [TextView] as they are the most likely to be wrongly covered by another view.
 */
object OverlapAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.OVERLAP

  override val backgroundEnabled: Boolean
    get() = OverlapAnalyzerInspection.overlapBackground

  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    val backgroundBounds = Rectangle(0, 0, 0, 0)
    val foregroundBounds = Rectangle(0, 0, 0, 0)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      findOverlapOfTextViewIssues(view, backgroundBounds, foregroundBounds, model, issues)
    }
    return issues
  }

  private fun findOverlapOfTextViewIssues(
    view: ViewInfo,
    backgroundBounds: Rectangle,
    foregroundBounds: Rectangle,
    model: NlModel,
    issueList: MutableList<VisualLintIssueContent>
  ) {
    val children =
      view.children.filter {
        it.accessibilityObject != null ||
          (it.cookie != null && (it.viewObject as? View)?.visibility == View.VISIBLE)
      }
    for (i in children.indices) {
      val firstView = children[i]
      if (!checkIsClass(firstView, TextView::class.java)) {
        continue
      }
      for (j in children.indices) {
        val secondView = children[j]
        if (firstView == secondView) {
          continue
        }
        if (
          isPartiallyHidden(
            firstView,
            i,
            backgroundBounds,
            secondView,
            j,
            foregroundBounds,
            model,
            view
          )
        ) {
          issueList.add(createIssueContent(firstView, secondView))
        }
      }
    }
  }

  private fun createIssueContent(
    firstView: ViewInfo,
    secondView: ViewInfo
  ): VisualLintIssueContent {
    val summary = "${nameWithId(firstView)} is covered by ${nameWithId(secondView)}"
    val content = { count: Int ->
      HtmlBuilder()
        .add(
          "Content of ${nameWithId(firstView)} is partially covered by ${nameWithId(secondView)} in ${previewConfigurations(count)}."
        )
        .newline()
        .add("This may affect text readability. Fix this issue by adjusting widget positioning.")
    }
    return VisualLintIssueContent(
      view = firstView,
      message = summary,
      descriptionProvider = content
    )
  }

  /**
   * Given two view infos, and their respective indices in layout, figure out of [firstViewInfo] is
   * being overlapped by [secondViewInfo] and if the ratio of the area of the overlap region to the
   * area of the [firstViewInfo] is bigger than [OVERLAP_RATIO_THRESHOLD].
   */
  private fun isPartiallyHidden(
    firstViewInfo: ViewInfo,
    i: Int,
    firstBounds: Rectangle,
    secondViewInfo: ViewInfo,
    j: Int,
    secondBounds: Rectangle,
    model: NlModel,
    parentViewInfo: ViewInfo
  ): Boolean {
    if (!isFirstViewUnderneath(firstViewInfo, i, secondViewInfo, j, model)) {
      return false
    }
    getTextBounds(firstViewInfo, firstBounds, parentViewInfo)
    if (firstBounds.width == 0 || firstBounds.height == 0) {
      return false
    }
    secondBounds.setBounds(
      secondViewInfo.left,
      secondViewInfo.top,
      secondViewInfo.right - secondViewInfo.left,
      secondViewInfo.bottom - secondViewInfo.top
    )
    val intersection = firstBounds.intersection(secondBounds)
    if (intersection.isEmpty) {
      return false
    }
    val coveredRatio =
      1.0 * intersection.width * intersection.height / (firstBounds.width * firstBounds.height)
    return coveredRatio >= OVERLAP_RATIO_THRESHOLD
  }

  /**
   * Given two view infos, and their respective indices in layout, figure out if [firstViewInfo] is
   * being drawn below [secondViewInfo].
   */
  private fun isFirstViewUnderneath(
    firstViewInfo: ViewInfo,
    firstViewIndex: Int,
    secondViewInfo: ViewInfo,
    secondViewIndex: Int,
    model: NlModel
  ): Boolean {
    val comp1 = componentFromViewInfo(firstViewInfo, model)
    val comp2 = componentFromViewInfo(secondViewInfo, model)

    // Try to see if we can compare elevation attribute if it exists.
    if (comp1 != null && comp2 != null) {
      val elev1 =
        ConstraintComponentUtilities.getDpValue(
          comp1,
          comp1.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION)
        )
      val elev2 =
        ConstraintComponentUtilities.getDpValue(
          comp2,
          comp2.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ELEVATION)
        )

      if (elev1 < elev2) {
        return true
      } else if (elev1 > elev2) {
        return false
      }
      // If they're the same, leave it to the index to resolve overlapping logic.
    }

    if (
      secondViewInfo.accessibilityObject != null && checkIsClass(secondViewInfo, Button::class.java)
    ) {
      // In compose, Buttons and the text inside them are two siblings components.
      // We ignore this case as it is not a case of hidden text
      return false
    }
    // else rely on index.
    return firstViewIndex < secondViewIndex
  }

  private fun getTextBounds(view: ViewInfo, textBounds: Rectangle, parent: ViewInfo) {
    val width = view.right - view.left
    val height = view.bottom - view.top
    textBounds.setBounds(view.left, view.top, width, height)
    val data =
      (view.accessibilityObject as? AccessibilityNodeInfo)
        ?.extras
        ?.getParcelableArray(
          AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
          RectF::class.java
        )
    if (data.isNullOrEmpty()) {
      return
    }
    var left = Integer.MAX_VALUE
    var right = Integer.MIN_VALUE
    var top = Integer.MAX_VALUE
    var bottom = Integer.MIN_VALUE
    data.filterNotNull().forEach {
      left = min(left, it.left.toInt())
      right = max(right, ceil(it.right).toInt())
      top = min(top, it.top.toInt())
      bottom = max(bottom, ceil(it.bottom).toInt())
    }
    if (right >= left && bottom >= top) {
      val parentBounds =
        (parent.accessibilityObject as? AccessibilityNodeInfo)?.boundsInScreen ?: return
      textBounds.setBounds(
        left - parentBounds.left,
        top - parentBounds.top,
        right - left,
        bottom - top
      )
    }
  }
}

class OverlapAnalyzerInspection :
  VisualLintInspection(VisualLintErrorType.OVERLAP, "overlapBackground") {
  companion object {
    var overlapBackground = true
  }
}
