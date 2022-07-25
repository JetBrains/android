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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintHighlightingIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape

class WarningLayer(private val screenView: ScreenView) : Layer() {

  override fun paint(gc: Graphics2D) {
    val screenShape: Shape? = screenView.screenShape
    gc.color = Color.ORANGE
    gc.stroke = NlConstants.DASHED_STROKE
    val selectedIssueSources = getSelectedIssues().map { it.source }
    val relevantComponents = selectedIssueSources.filterIsInstance<VisualLintIssueProvider.VisualLintIssueSource>()
      .flatMap { it.components }
      .filter { it.model == screenView.sceneManager.model }
    relevantComponents.forEach {
      gc.drawRect(
        Coordinates.getSwingX(screenView, it.x),
        Coordinates.getSwingY(screenView, it.y),
        Coordinates.getSwingDimension(screenView, it.w),
        Coordinates.getSwingDimension(screenView, it.h))
    }
    gc.stroke = NlConstants.SOLID_STROKE
    val clip = gc.clip
    if (screenShape != null) {
      gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      gc.draw(screenShape)
      gc.clip = screenShape
    }
    else {
      val sceneSize = screenView.scaledContentSize
      gc.drawRect(screenView.x, screenView.y, sceneSize.width, sceneSize.height)
      gc.setClip(screenView.x, screenView.y, sceneSize.width, sceneSize.height)
    }
    relevantComponents.forEach {
      gc.drawRect(
        Coordinates.getSwingX(screenView, it.x),
        Coordinates.getSwingY(screenView, it.y),
        Coordinates.getSwingDimension(screenView, it.w),
        Coordinates.getSwingDimension(screenView, it.h))
    }
    gc.clip = clip
  }

  override val isVisible: Boolean
    get() {
      val selectedIssues = getSelectedIssues()
      return selectedIssues.filterIsInstance<VisualLintHighlightingIssue>().any { it.shouldHighlight(screenView.sceneManager.model) }
    }

  private fun getSelectedIssues(): List<Issue> {
    return if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      IssuePanelService.getInstance(screenView.surface.project).getSelectedIssues()
    }
    else {
      listOfNotNull(screenView.surface.issuePanel.selectedIssue)
    }
  }
}
