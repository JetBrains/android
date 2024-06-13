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

import android.widget.ScrollView
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder

private val scrollViewInterfaces =
  setOf(
    "com.android.internal.widget.ScrollingView",
    "androidx.core.view.ScrollingView",
    "androidx.core.view.NestedScrollingParent",
    "androidx.core.view.NestedScrollingParent2",
    "androidx.core.view.NestedScrollingParent3",
  )

/**
 * [VisualLintAnalyzer] for issues where a child view is not fully contained within the bounds of
 * its parent.
 */
object BoundsAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.BOUNDS

  override val backgroundEnabled: Boolean
    get() = BoundsAnalyzerInspection.boundsBackground

  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel,
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews.filterNot { isScrollingView(it) })
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      val width = view.right - view.left
      val height = view.bottom - view.top
      view.children.forEach {
        if (isScrollingView(it)) {
          return@forEach
        }
        viewsToAnalyze.addLast(it)
        if (isOutOfBounds(it, width, height)) {
          issues.add(createIssueContent(it))
        }
      }
    }
    return issues
  }

  private fun isOutOfBounds(child: ViewInfo, width: Int, height: Int): Boolean {
    // Bounds of children are defined relative to their parent
    return (child.top < 0 || child.bottom > height || child.left < 0 || child.right > width)
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val viewName = simpleName(view)
    val summary = "${nameWithId(view)} is partially hidden in layout"
    val provider = { count: Int ->
      HtmlBuilder()
        .add(
          "$viewName is partially hidden in layout because it is not contained within the bounds of its parent in ${
          previewConfigurations(count)
        }."
        )
        .newline()
        .add("Fix this issue by adjusting the size or position of $viewName.")
    }
    return VisualLintIssueContent(view = view, message = summary, descriptionProvider = provider)
  }

  private fun isScrollingView(view: ViewInfo): Boolean {
    if (view.viewObject is ScrollView) {
      return true
    }
    return view.viewObject.javaClass.interfaces.any { it.name in scrollViewInterfaces }
  }
}

class BoundsAnalyzerInspection :
  VisualLintInspection(VisualLintErrorType.BOUNDS, "boundsBackground") {
  companion object {
    var boundsBackground = true
  }
}
