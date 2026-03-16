/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.visuallint.analyzers

import android.widget.Button
import android.widget.TextView
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.visuallint.VisualLintAnalyzer
import com.android.tools.visuallint.VisualLintConfiguration
import com.android.tools.visuallint.VisualLintErrorType
import com.android.tools.visuallint.VisualLintRenderResult
import com.android.utils.HtmlBuilder
import java.awt.Rectangle

/**
 * [VisualLintAnalyzer] for issues where a view is covered by System UI. Limit to covered [TextView] and [Button] as they are the most
 * likely to be wrongly covered by another view.
 */
object SystemUiAnalyzer : VisualLintAnalyzer() {
  private const val STATUS_BAR_CLASS = "com.android.layoutlib.bridge.bars.StatusBar"
  private const val NAVIGATION_BAR_CLASS = "com.android.layoutlib.bridge.bars.NavigationBar"
  private const val DISPLAY_CUTOUT_CLASS = "com.android.layoutlib.bridge.bars.DisplayCutout"
  private const val EDGE_TO_EDGE_LINK = "https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge"

  override val type: VisualLintErrorType
    get() = VisualLintErrorType.SYSTEM_UI

  override fun findIssues(renderResult: VisualLintRenderResult, configuration: VisualLintConfiguration): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()

    val systemUiBounds = mutableListOf<Rectangle>()
    traverseViewHierarchy(renderResult.systemRootViews) { view, viewBounds ->
      if (view.className == STATUS_BAR_CLASS || view.className == NAVIGATION_BAR_CLASS || view.className == DISPLAY_CUTOUT_CLASS) {
        systemUiBounds.add(viewBounds)
      }
    }

    if (systemUiBounds.isEmpty()) {
      return issues
    }

    val viewsToCheck = mutableListOf<Pair<ViewInfo, Rectangle>>()
    traverseViewHierarchy(renderResult.rootViews) { view, viewBounds ->
      if (checkIsClass(view, TextView::class.java) || checkIsClass(view, Button::class.java)) {
        viewsToCheck.add(view to viewBounds)
      }
    }

    for ((view, viewBounds) in viewsToCheck) {
      for (sysUiBound in systemUiBounds) {
        if (viewBounds.intersects(sysUiBound)) {
          issues.add(createIssueContent(view))
          break
        }
      }
    }

    return issues
  }

  private fun traverseViewHierarchy(roots: List<ViewInfo>, onNode: (ViewInfo, Rectangle) -> Unit) {
    val deque = ArrayDeque<Pair<ViewInfo, Rectangle>>()
    roots.forEach { deque.addLast(it to Rectangle(it.left, it.top, it.right - it.left, it.bottom - it.top)) }
    while (deque.isNotEmpty()) {
      val (view, viewBounds) = deque.removeLast()
      onNode(view, viewBounds)
      view.children.forEach { child ->
        val childBounds = Rectangle(viewBounds.x + child.left, viewBounds.y + child.top, child.right - child.left, child.bottom - child.top)
        deque.addLast(child to childBounds)
      }
    }
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val viewName = nameWithId(view)
    val summary = "$viewName is covered by System UI"
    val content = { count: Int ->
      HtmlBuilder()
        .add("Content of $viewName is partially covered by System UI in ${previewConfigurations(count)}.")
        .newline()
        .add("This may affect interactivity and readability. See ")
        .addLink("edge-to-edge design", EDGE_TO_EDGE_LINK)
        .add(" recommendations.")
    }
    return VisualLintIssueContent(view = view, message = summary, descriptionProvider = content)
  }
}
