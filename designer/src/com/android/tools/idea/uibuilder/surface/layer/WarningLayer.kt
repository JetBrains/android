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
package com.android.tools.idea.uibuilder.surface.layer

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.IssueNode
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Area

open class WarningLayer(
  protected val screenView: ScreenView,
  private val shouldDisplay: () -> Boolean
) : Layer() {

  protected var componentsToHighlight: List<NlComponent> = emptyList()
  private val issueListener =
    object : IssueListener {
      override fun onIssueSelected(issue: Issue?) {
        componentsToHighlight = issue?.getComponentsToHighlight() ?: emptyList()
        screenView.surface.repaint()
      }
    }

  private val tabSelectionListener =
    object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        val selectedItem =
          (event.content.component as? DataProvider)?.getData(PlatformDataKeys.SELECTED_ITEM.name)
        componentsToHighlight =
          (selectedItem as? IssueNode)?.issue?.getComponentsToHighlight() ?: emptyList()
        screenView.surface.repaint()
      }
    }

  init {
    screenView.surface.addIssueListener(issueListener)
    ProblemsView.getToolWindow(screenView.surface.project)
      ?.contentManager
      ?.addContentManagerListener(tabSelectionListener)
  }

  override fun paint(gc: Graphics2D) {
    val screenShape: Shape? = screenView.screenShape
    gc.color = JBColor.ORANGE
    gc.stroke = NlConstants.DASHED_STROKE
    componentsToHighlight.forEach {
      gc.drawRect(
        Coordinates.getSwingX(screenView, it.x),
        Coordinates.getSwingY(screenView, it.y),
        Coordinates.getSwingDimension(screenView, it.w),
        Coordinates.getSwingDimension(screenView, it.h)
      )
    }
    gc.stroke = NlConstants.SOLID_STROKE
    val clip = gc.clip
    if (screenShape != null) {
      gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      gc.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BICUBIC
      )
      gc.draw(screenShape)
      val screenShapeClip = Area(screenShape).apply { intersect(Area(clip)) }
      gc.clip = screenShapeClip
    } else {
      val sceneSize = screenView.scaledContentSize
      gc.drawRect(screenView.x, screenView.y, sceneSize.width, sceneSize.height)
      gc.clipRect(screenView.x, screenView.y, sceneSize.width, sceneSize.height)
    }
    componentsToHighlight.forEach {
      gc.drawRect(
        Coordinates.getSwingX(screenView, it.x),
        Coordinates.getSwingY(screenView, it.y),
        Coordinates.getSwingDimension(screenView, it.w),
        Coordinates.getSwingDimension(screenView, it.h)
      )
    }
    gc.clip = clip
  }

  override val isVisible: Boolean
    get() = componentsToHighlight.isNotEmpty() && shouldDisplay()

  override fun dispose() {
    screenView.surface.removeIssueListener(issueListener)
    ProblemsView.getToolWindow(screenView.surface.project)
      ?.contentManager
      ?.removeContentManagerListener(tabSelectionListener)
    super.dispose()
  }

  private fun Issue.getComponentsToHighlight(): List<NlComponent> {
    return (source as? VisualLintIssueProvider.VisualLintIssueSource)
      ?.components
      ?.filter { it.model == screenView.sceneManager.model }
      ?.toList() ?: emptyList()
  }
}
