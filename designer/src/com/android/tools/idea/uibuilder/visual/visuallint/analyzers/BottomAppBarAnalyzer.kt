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
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder

private const val BOTTOM_APP_BAR_CLASS_NAME =
  "com.google.android.material.bottomappbar.BottomAppBar"
private const val NAVIGATION_RAIL_URL =
  "https://d.android.com/r/studio-ui/designer/material/navigation-rail"
private const val NAVIGATION_DRAWER_URL =
  "https://d.android.com/r/studio-ui/designer/material/navigation-drawer"
private const val TOP_APP_BAR_URL =
  "https://d.android.com/r/studio-ui/designer/material/top-app-bar"

/** [VisualLintAnalyzer] for issues where a BottomAppBar is used on non-compact screens. */
object BottomAppBarAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.BOTTOM_APP_BAR

  override val backgroundEnabled: Boolean
    get() = BottomAppBarAnalyzerInspection.bottomAppBarBackground

  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel,
  ): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val configuration = model.configuration
    val orientation = configuration.deviceState?.orientation ?: return issues
    val dimension = configuration.device?.getScreenSize(orientation) ?: return issues
    val width = Coordinates.pxToDp(model, dimension.width)
    val height = Coordinates.pxToDp(model, dimension.height)
    if (width > 600 && height > 360) {
      val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
      while (viewsToAnalyze.isNotEmpty()) {
        val view = viewsToAnalyze.removeLast()
        view.children.forEach { viewsToAnalyze.addLast(it) }
        if (view.className == BOTTOM_APP_BAR_CLASS_NAME) {
          issues.add(createIssueContent(view))
        }
      }
    }
    return issues
  }

  private fun createIssueContent(view: ViewInfo): VisualLintIssueContent {
    val content = { count: Int ->
      HtmlBuilder()
        .add("Bottom app bars are only recommended for compact screens, ")
        .add("which affects ${previewConfigurations(count)}.")
        .newline()
        .add("Material Design recommends replacing bottom app bar with ")
        .addLink("navigation rail", NAVIGATION_RAIL_URL)
        .add(", ")
        .addLink("navigation drawer", NAVIGATION_DRAWER_URL)
        .add(" or ")
        .addLink("top app bar", TOP_APP_BAR_URL)
        .add(" for breakpoints over 600dp.")
    }
    return VisualLintIssueContent(
      view = view,
      message = "Bottom app bars are only recommended for compact screens",
      descriptionProvider = content,
    )
  }
}

class BottomAppBarAnalyzerInspection :
  VisualLintInspection(VisualLintErrorType.BOTTOM_APP_BAR, "bottomAppBarBackground") {
  companion object {
    var bottomAppBarBackground = true
  }
}
