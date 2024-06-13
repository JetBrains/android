/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.common.surface.Layer
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting const val PLACEHOLDER_TEXT = "Loading Overlay..."

@VisibleForTesting const val PLACEHOLDER_ALPHA = 0.7f

/** The Overlay Layer to be displayed on top of the layout preview */
class OverlayLayer(
  private val sceneView: SceneView,
  private val overlayConfiguration: () -> OverlayConfiguration,
) : Layer() {
  private var screenViewSize = Dimension()

  private fun paintPlaceholder(g: Graphics2D) {
    g.composite = AlphaComposite.SrcOver.derive(PLACEHOLDER_ALPHA)
    g.paint = JBColor.WHITE
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    if (sceneView.screenShape != null) {
      g.fill(sceneView.screenShape)
    } else {
      g.fillRect(sceneView.x, sceneView.y, screenViewSize.width, screenViewSize.height)
    }
    g.font = UIUtil.getFont(UIUtil.FontSize.NORMAL, null)
    g.paint = JBColor.BLACK
    val textLayout = TextLayout(PLACEHOLDER_TEXT, g.font, g.fontRenderContext)
    val textHeight = textLayout.bounds.height
    val textWidth = textLayout.bounds.width
    g.drawString(
      PLACEHOLDER_TEXT,
      sceneView.x + screenViewSize.width / 2 - textWidth.toInt() / 2,
      sceneView.y + screenViewSize.height / 2 + textHeight.toInt() / 2,
    )
  }

  private fun paintOverlay(g: Graphics2D, image: BufferedImage?) {
    val overlayConfiguration = overlayConfiguration()
    if (image == null) {
      return
    }
    g.composite = AlphaComposite.SrcOver.derive(overlayConfiguration.overlayAlpha)
    g.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    )
    g.drawImage(image, sceneView.x, sceneView.y, screenViewSize.width, screenViewSize.height, null)
  }

  override fun paint(gc: Graphics2D) {
    val overlayConfiguration = overlayConfiguration()
    if (overlayConfiguration.overlayVisibility) {
      screenViewSize = sceneView.getScaledContentSize(screenViewSize)
      if (overlayConfiguration.isPlaceholderVisible) {
        paintPlaceholder(gc)
      } else {
        paintOverlay(gc, overlayConfiguration.overlayImage)
      }
    }
  }
}
