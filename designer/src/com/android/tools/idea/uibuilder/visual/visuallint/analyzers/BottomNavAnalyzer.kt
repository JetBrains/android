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

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.configurations.Configuration
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder

private const val BOTTOM_NAVIGATION_CLASS_NAME =
  "com.google.android.material.bottomnavigation.BottomNavigationView"
private const val NAVIGATION_RAIL_URL =
  "https://d.android.com/r/studio-ui/designer/material/navigation-rail"
private const val NAVIGATION_DRAWER_URL =
  "https://d.android.com/r/studio-ui/designer/material/navigation-drawer"

/** [VisualLintAnalyzer] for issues where a BottomNavigationView is wider than 600dp. */
object BottomNavAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.BOTTOM_NAV

  override fun findIssues(
    renderResult: RenderResult,
    configuration: Configuration,
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      if (view.className == BOTTOM_NAVIGATION_CLASS_NAME) {
        /* This is needed, as visual lint analysis need to run outside the context of scene. */
        val widthInDp = pxToDp(configuration, view.right - view.left)
        if (widthInDp > 600) {
          issues.add(createIssueContent(view))
        }
      }
    }
    return issues
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val content = { count: Int ->
      HtmlBuilder()
        .add("Bottom navigation bar is not recommended for breakpoints >= 600dp, ")
        .add("which affects ${previewConfigurations(count)}.")
        .newline()
        .add("Material Design recommends replacing bottom navigation bar with ")
        .addLink("navigation rail", NAVIGATION_RAIL_URL)
        .add(" or ")
        .addLink("navigation drawer", NAVIGATION_DRAWER_URL)
        .add(" for breakpoints >= 600dp.")
    }
    return VisualLintIssueContent(
      view = view,
      message = "Bottom navigation bar is not recommended for breakpoints over 600dp",
      descriptionProvider = content,
    )
  }
}
