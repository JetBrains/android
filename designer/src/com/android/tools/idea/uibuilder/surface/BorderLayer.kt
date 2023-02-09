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
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints

class BorderLayer @JvmOverloads constructor(private val myScreenView: SceneView,
                                            private val myMustPaintBorder: Boolean = false) : Layer() {
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
    BorderPainter.paint(g2d, myScreenView)
  }
}

private object BorderPainter {
  private val SHADOW_SIZE = JBUI.scale(4)
  private val COLOR_OUTSIDE = UIUtil.TRANSPARENT_COLOR
  private val COLOR_INSIDE: Color = JBColor.namedColor("ScreenView.borderColor", JBColor(Color(0, 0, 0, 40), Color(0, 0, 0, 80)))
  private val GRAD_LEFT: Paint = GradientPaint(0f, 0f, COLOR_OUTSIDE, SHADOW_SIZE.toFloat(), 0f, COLOR_INSIDE)
  private val GRAD_TOP: Paint = GradientPaint(0f, 0f, COLOR_OUTSIDE, 0f, SHADOW_SIZE.toFloat(), COLOR_INSIDE)
  private val GRAD_RIGHT: Paint = GradientPaint(0f, 0f, COLOR_INSIDE, SHADOW_SIZE.toFloat(), 0f, COLOR_OUTSIDE)
  private val GRAD_BOTTOM: Paint = GradientPaint(0f, 0f, COLOR_INSIDE, 0f, SHADOW_SIZE.toFloat(), COLOR_OUTSIDE)
  private val GRAD_CORNER: Paint = RadialGradientPaint(SHADOW_SIZE.toFloat(), SHADOW_SIZE.toFloat(), SHADOW_SIZE.toFloat(),
                                                       floatArrayOf(0f, 1f), arrayOf(COLOR_INSIDE, COLOR_OUTSIDE))

  fun paint(g2d: Graphics2D, screenView: SceneView) {
    val size = screenView.scaledContentSize
    val x = screenView.x
    val y = screenView.y
    val hints = g2d.renderingHints
    val tx = g2d.transform
    val paint = g2d.paint

    // Left
    g2d.translate(x - SHADOW_SIZE, y)
    g2d.scale(1.0, size.height / SHADOW_SIZE.toDouble())
    g2d.paint = GRAD_LEFT
    g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE)

    // Right
    g2d.translate(size.width + SHADOW_SIZE, 0)
    g2d.paint = GRAD_RIGHT
    g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE)

    // Reset transform scale and translate to upper left corner
    g2d.translate(-size.width, 0)
    g2d.scale(1.0, SHADOW_SIZE / size.height.toDouble())

    // Top
    g2d.translate(0, -SHADOW_SIZE)
    g2d.scale(size.width / SHADOW_SIZE.toDouble(), 1.0)
    g2d.paint = GRAD_TOP
    g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE)

    // Bottom
    g2d.translate(0, size.height + SHADOW_SIZE)
    g2d.paint = GRAD_BOTTOM
    g2d.fillRect(0, 0, SHADOW_SIZE, SHADOW_SIZE)

    // Reset the transform
    g2d.transform = tx

    // Smoothen the corner shadows
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    // Paint the corner shadows
    g2d.paint = GRAD_CORNER
    // Top Left
    g2d.translate(x - SHADOW_SIZE, y - SHADOW_SIZE)
    g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 90, 90)
    // Top Right
    g2d.translate(size.width, 0)
    g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 0, 90)
    // Bottom Right
    g2d.translate(0, size.height)
    g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 270, 90)
    // Bottom Left
    g2d.translate(-size.width, 0)
    g2d.fillArc(0, 0, SHADOW_SIZE * 2, SHADOW_SIZE * 2, 180, 90)
    g2d.transform = tx
    g2d.setRenderingHints(hints)
    g2d.paint = paint
  }
}
