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
package com.android.tools.idea.uibuilder.surface.layer

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

private val defaultBorderColor =
  JBColor.namedColor(
    "ScreenView.defaultBorderColor",
    JBColor(Color(0, 0, 0, 40), Color(0, 0, 0, 80)),
  )
private val borderColor =
  JBColor.namedColor("ScreenView.selectedBorderColor", JBColor(0x3573f0, 0x548af7))

enum class BorderColor(
  internal val colorInside: Color,
  internal val colorOutside: Color,
  internal val size: Int,
) {
  DEFAULT_WITH_SHADOW(defaultBorderColor, UIUtil.TRANSPARENT_COLOR, 4),
  DEFAULT_WITHOUT_SHADOW(defaultBorderColor, defaultBorderColor, 1),
  SELECTED(borderColor, borderColor, 2),
  HOVERED(borderColor, borderColor, 1),
}

class BorderLayer
@JvmOverloads
constructor(
  private val myScreenView: SceneView,
  private val myMustPaintBorder: Boolean = false,
  private val isRotating: () -> Boolean,
  private val colorProvider: (SceneView) -> BorderColor = { BorderColor.DEFAULT_WITH_SHADOW },
) : Layer() {
  override fun paint(g2d: Graphics2D) {
    val screenShape = myScreenView.screenShape
    if (!myMustPaintBorder && screenShape != null) {
      g2d.draw(screenShape)
      return
    }

    // When screen rotation feature is enabled, we want to hide the border.
    if (isRotating()) {
      return
    }

    val borderColor = colorProvider(myScreenView)
    BorderPainter(
        JBUI.scale(borderColor.size),
        borderColor.colorInside,
        borderColor.colorOutside,
        useHighQuality = true,
      )
      .paint(g2d, myScreenView)
  }
}

class BorderPainter(
  private val borderThickness: Int,
  colorInside: Color,
  colorOutside: Color,
  val useHighQuality: Boolean,
) {
  private val gradLeft =
    GradientPaint(0f, 0f, colorOutside, borderThickness.toFloat(), 0f, colorInside)
  private val gradTop =
    GradientPaint(0f, 0f, colorOutside, 0f, borderThickness.toFloat(), colorInside)
  private val gradRight =
    GradientPaint(0f, 0f, colorInside, borderThickness.toFloat(), 0f, colorOutside)
  private val gradBottom =
    GradientPaint(0f, 0f, colorInside, 0f, borderThickness.toFloat(), colorOutside)
  private val gradCorner =
    RadialGradientPaint(
      borderThickness.toFloat(),
      borderThickness.toFloat(),
      borderThickness.toFloat(),
      floatArrayOf(0f, 1f),
      arrayOf(colorInside, colorOutside),
    )

  fun paint(g2d: Graphics2D, screenView: SceneView) {
    val size = screenView.scaledContentSize
    paint(g2d, screenView.x, screenView.y, size.width, size.height)
  }

  fun paint(g2d: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    val hints = g2d.renderingHints
    val tx = g2d.transform
    val paint = g2d.paint

    // Left
    g2d.translate(x - borderThickness, y)
    g2d.scale(1.0, height / borderThickness.toDouble())
    g2d.paint = gradLeft
    g2d.fillRect(0, 0, borderThickness, borderThickness)

    // Right
    g2d.translate(width + borderThickness, 0)
    g2d.paint = gradRight
    g2d.fillRect(0, 0, borderThickness, borderThickness)

    // Reset transform scale and translate to upper left corner
    g2d.translate(-width, 0)
    g2d.scale(1.0, borderThickness / height.toDouble())

    // Top
    g2d.translate(0, -borderThickness)
    g2d.scale(width / borderThickness.toDouble(), 1.0)
    g2d.paint = gradTop
    g2d.fillRect(0, 0, borderThickness, borderThickness)

    // Bottom
    g2d.translate(0, height + borderThickness)
    g2d.paint = gradBottom
    g2d.fillRect(0, 0, borderThickness, borderThickness)

    // Reset the transform
    g2d.transform = tx

    if (useHighQuality) {
      // Smoothen the corner shadows
      g2d.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
      )
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    }

    // Paint the corner shadows
    g2d.paint = gradCorner
    // Top Left
    g2d.translate(x - borderThickness, y - borderThickness)
    g2d.fillArc(0, 0, borderThickness * 2, borderThickness * 2, 90, 90)
    // Top Right
    g2d.translate(width, 0)
    g2d.fillArc(0, 0, borderThickness * 2, borderThickness * 2, 0, 90)
    // Bottom Right
    g2d.translate(0, height)
    g2d.fillArc(0, 0, borderThickness * 2, borderThickness * 2, 270, 90)
    // Bottom Left
    g2d.translate(-width, 0)
    g2d.fillArc(0, 0, borderThickness * 2, borderThickness * 2, 180, 90)
    g2d.transform = tx
    g2d.setRenderingHints(hints)
    g2d.paint = paint
  }
}
