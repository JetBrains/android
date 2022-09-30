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
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.utils.HtmlBuilder

private const val BOTTOM_NAVIGATION_CLASS_NAME = "com.google.android.material.bottomnavigation.BottomNavigationView"
private const val NAVIGATION_RAIL_CLASS_NAME = "com.google.android.material.navigationrail.NavigationRailView"
private const val NAVIGATION_RAIL_URL = "https://material.io/components/navigation-rail/android"
private const val NAVIGATION_DRAWER_URL = "https://material.io/components/navigation-drawer/android"

/**
 * [VisualLintAnalyzer] for issues where a BottomNavigationView is wider than 600dp.
 */
object BottomNavAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.BOTTOM_NAV

  override val backgroundEnabled: Boolean
    get() = BottomNavAnalyzerInspection.bottomNavBackground

  override fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
    val configuration = model.configuration
    val orientation = configuration.deviceState?.orientation ?: return issues
    val dimension = configuration.device?.getScreenSize(orientation) ?: return issues
    val deviceWidthInDp = Coordinates.pxToDp(model, dimension.width)
    while (viewsToAnalyze.isNotEmpty()) {
      val view = viewsToAnalyze.removeLast()
      view.children.forEach { viewsToAnalyze.addLast(it) }
      if (view.className == BOTTOM_NAVIGATION_CLASS_NAME && deviceWidthInDp >= 600) {
        issues.add(createIssueContentForBottomNavigation(view))
      } else if (view.className == NAVIGATION_RAIL_CLASS_NAME && deviceWidthInDp >= 1240) {
        issues.add(createIssueContentForNavigationRail(view))
      }
    }
    return issues
  }

  private fun createIssueContentForBottomNavigation(view: ViewInfo): VisualLintIssueContent {
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
    return VisualLintIssueContent(view, "Bottom navigation bar is not recommended for breakpoints over 600dp", content)
  }

  private fun createIssueContentForNavigationRail(view: ViewInfo): VisualLintIssueContent {
    val content = { count: Int ->
      HtmlBuilder()
        .add("Navigation rail is not recommended for breakpoints >= 1240dp, ")
        .add("which affects ${previewConfigurations(count)}.")
        .newline()
        .add("Material Design recommends replacing navigation rail with ")
        .addLink("navigation drawer", NAVIGATION_DRAWER_URL)
        .add(" for breakpoints >= 1240dp.")
    }
    return VisualLintIssueContent(view, "Navigation rail is not recommended for breakpoints over 1240dp", content)
  }
}

object BottomNavAnalyzerInspection: VisualLintInspection(VisualLintErrorType.BOTTOM_NAV, "bottomNavBackground") {
  var bottomNavBackground = true
}
