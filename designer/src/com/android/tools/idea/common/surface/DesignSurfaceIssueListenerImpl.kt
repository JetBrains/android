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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.NlComponentIssueSource
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.pom.Navigatable
import java.awt.Dimension
import kotlin.math.max
import kotlin.math.min

private const val MARGIN = 20

/**
 * The [IssueListener] of [DesignSurface].
 * TODO?: Consider to move as inner class of [DesignSurface]?
 */
class DesignSurfaceIssueListenerImpl(val surface: DesignSurface<*>) : IssueListener{
  override fun onIssueSelected(issue: Issue) {
    when (val source = issue.source) {
      is NlComponentIssueSource -> {
        val component = source.component
        surface.selectionModel.setSelection(listOf(component))

        // Navigate to the selected element if possible
        val element = component.backend.tag?.navigationElement
        if (element is Navigatable && PsiNavigationSupport.getInstance().canNavigate(element)) {
          (element as Navigatable).navigate(false)
        }
      }
      is VisualLintIssueProvider.VisualLintIssueSource -> {
        // Repaint DesignSurface when issue is selected to update visibility of WarningLayer
        surface.repaint()
        val sceneViews = surface.sceneManagers.filter { source.models.contains(it.model) }.flatMap { it.sceneViews }
        if (sceneViews.isEmpty()) {
          return
        }
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        sceneViews.forEach {
          minX = min(minX, it.x)
          minY = min(minY, it.y)
          maxX = max(maxX, it.x + it.scaledContentSize.width)
          maxY = max(maxY, it.y + it.scaledContentSize.height)
        }
        val currentScale = surface.scale
        val scale = surface.getFitScale(
          Dimension(((maxX - minX + 2 * MARGIN) / currentScale).toInt(), ((maxY - minY + 2 * MARGIN) / currentScale).toInt()), false)
        surface.setScale(scale)
        surface.setScrollPosition(minX - MARGIN, minY - MARGIN)
      }
      else -> Unit
    }
  }
}