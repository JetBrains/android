/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.idea.common.surface.SceneView
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints

private val defaultBorderColor = JBColor.namedColor(
  "ScreenView.defaultBorderColor",
  JBColor(Color(0, 0, 0, 40), Color(0, 0, 0, 80))
)
private val selectedBorderColor = JBColor.namedColor(
  "ScreenView.selectedBorderColor",
  JBColor(0x3573f0, 0x548af7)
)
private val hoveredBorderColor = JBColor.namedColor(
  "ScreenView.hoveredBorderColor",
  JBColor(0x3573f0, 0x548af7)
)

enum class BorderColor(internal val colorInside: Color, internal val colorOutside: Color, internal val size: Int) {
  DEFAULT_WITH_SHADOW(defaultBorderColor, UIUtil.TRANSPARENT_COLOR, 4),
  DEFAULT_WITHOUT_SHADOW(defaultBorderColor, defaultBorderColor, 1),
  SELECTED(selectedBorderColor, selectedBorderColor, 1),
  HOVERED(hoveredBorderColor, hoveredBorderColor, 2);
}

class BorderLayer @JvmOverloads constructor(private val myScreenView: SceneView,
                                            private val myMustPaintBorder: Boolean = false,
                                            private val colorProvider: (SceneView) -> BorderColor = {
                                              BorderColor.DEFAULT_WITH_SHADOW
                                            }) : Layer() {
  override fun paint(g2d: Graphics2D) {
    val screenShape = myScreenView.screenShape
    if (!myMustPaintBorder && screenShape != null) {
      g2d.draw(screenShape)
      return
    }

    // When screen rotation feature is enabled, we want to hide the border.
    val surface = myScreenView.surface
    if (surface is NlDesignSurface) {
      val degree = surface.rotateSurfaceDegree
      if (!java.lang.Float.isNaN(degree)) {
        return
      }
    }
    BorderPainter.paint(g2d, myScreenView, colorProvider(myScreenView))
  }
}

private object BorderPainter {
  fun paint(g2d: Graphics2D, screenView: SceneView, colorConfig: BorderColor) {
    val borderSize = JBUI.scale(colorConfig.size)
    val gradLeft = GradientPaint (0f, 0f, colorConfig.colorOutside, borderSize.toFloat(), 0f, colorConfig.colorInside)
    val gradTop = GradientPaint(0f, 0f, colorConfig.colorOutside, 0f, borderSize.toFloat(), colorConfig.colorInside)
    val gradRight = GradientPaint(0f, 0f, colorConfig.colorInside, borderSize.toFloat(), 0f, colorConfig.colorOutside)
    val gradBottom = GradientPaint(0f, 0f, colorConfig.colorInside, 0f, borderSize.toFloat(), colorConfig.colorOutside)
    val gradCorner = RadialGradientPaint(borderSize.toFloat(), borderSize.toFloat(), borderSize.toFloat(),
                                                  floatArrayOf(0f, 1f), arrayOf(colorConfig.colorInside, colorConfig.colorOutside))
    val size = screenView.scaledContentSize
    val x = screenView.x
    val y = screenView.y
    val hints = g2d.renderingHints
    val tx = g2d.transform
    val paint = g2d.paint

    // Left
    g2d.translate(x - borderSize, y)
    g2d.scale(1.0, size.height / borderSize.toDouble())
    g2d.paint = gradLeft
    g2d.fillRect(0, 0, borderSize, borderSize)

    // Right
    g2d.translate(size.width + borderSize, 0)
    g2d.paint = gradRight
    g2d.fillRect(0, 0, borderSize, borderSize)

    // Reset transform scale and translate to upper left corner
    g2d.translate(-size.width, 0)
    g2d.scale(1.0, borderSize / size.height.toDouble())

    // Top
    g2d.translate(0, -borderSize)
    g2d.scale(size.width / borderSize.toDouble(), 1.0)
    g2d.paint = gradTop
    g2d.fillRect(0, 0, borderSize, borderSize)

    // Bottom
    g2d.translate(0, size.height + borderSize)
    g2d.paint = gradBottom
    g2d.fillRect(0, 0, borderSize, borderSize)

    // Reset the transform
    g2d.transform = tx

    // Smoothen the corner shadows
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    // Paint the corner shadows
    g2d.paint = gradCorner
    // Top Left
    g2d.translate(x - borderSize, y - borderSize)
    g2d.fillArc(0, 0, borderSize * 2, borderSize * 2, 90, 90)
    // Top Right
    g2d.translate(size.width, 0)
    g2d.fillArc(0, 0, borderSize * 2, borderSize * 2, 0, 90)
    // Bottom Right
    g2d.translate(0, size.height)
    g2d.fillArc(0, 0, borderSize * 2, borderSize * 2, 270, 90)
    // Bottom Left
    g2d.translate(-size.width, 0)
    g2d.fillArc(0, 0, borderSize * 2, borderSize * 2, 180, 90)
    g2d.transform = tx
    g2d.setRenderingHints(hints)
    g2d.paint = paint
  }
}
