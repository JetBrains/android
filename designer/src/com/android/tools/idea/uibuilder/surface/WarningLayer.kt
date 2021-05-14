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

import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintHighlightingIssue
import com.intellij.ui.scale.JBUIScale
import icons.StudioIcons
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape

class WarningLayer(private val screenView: ScreenView) : Layer() {

  override fun paint(gc: Graphics2D) {
    val screenShape: Shape? = screenView.screenShape
    gc.color = Color.ORANGE
    gc.stroke = BasicStroke(JBUIScale.scale(4.0f))
    if (screenShape != null) {
      gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      gc.draw(screenShape)
      return
    }
    val sceneSize = screenView.scaledContentSize
    gc.drawRect(screenView.x, screenView.y, sceneSize.width, sceneSize.height)
    val icon = StudioIcons.Common.WARNING
    icon.paintIcon(screenView.surface, gc, screenView.x + sceneSize.width - icon.iconWidth - 1, screenView.y + 1)
  }

  override val isVisible: Boolean
    get() {
      val selectedIssue = screenView.surface.issuePanel.selectedIssue
      if (selectedIssue is VisualLintHighlightingIssue) {
        return selectedIssue.shouldHighlight(screenView.sceneManager.model)
      }
      return false
    }
}
