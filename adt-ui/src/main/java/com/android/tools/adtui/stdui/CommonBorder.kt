/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.model.stdui.CommonBorderModel
import com.android.tools.adtui.model.stdui.DefaultCommonBorderModel
import com.android.tools.adtui.stdui.StandardColors.*
import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
import com.android.tools.adtui.stdui.StandardDimensions.INNER_BORDER_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.OUTER_BORDER_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.VERTICAL_PADDING
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.border.Border

/**
 * A [Border] that has curved edges.
 *
 * The radius of the outer corner is specified in [cornerRadius].
 * The border will change color based on the values in the [model].
 * These states are supported:
 *  - enabled/disabled state based on the component state.
 *  - focused/unfocused state based on existence of a focus owner in the component or one of its children.
 *  - error state based on the [model].
 *  - placeholder state based on the [model].
 */
class CommonBorder(private val cornerRadius: Float,
                   private val model: CommonBorderModel = DefaultCommonBorderModel(),
                   val paddingTop: Int = VERTICAL_PADDING,
                   val paddingLeft: Int = HORIZONTAL_PADDING,
                   val paddingBottom: Int = VERTICAL_PADDING,
                   val paddingRight: Int = HORIZONTAL_PADDING)
  : Border {

  override fun getBorderInsets(component: Component): Insets {
    val insets = Insets(paddingTop, paddingLeft, paddingBottom, paddingRight)
    val inset = Math.round(INNER_BORDER_WIDTH + OUTER_BORDER_WIDTH)
    insets.left += inset
    insets.right += inset
    insets.top += inset
    insets.bottom += inset
    return insets
  }

  override fun isBorderOpaque() = true

  override fun paintBorder(component: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g as Graphics2D
    val rect = Rectangle2D.Float(0f, 0f, component.width.toFloat(), component.height.toFloat())
    val (outerColor, innerColor) = when {
      !component.isEnabled ->
        Pair(UIUtil.TRANSPARENT_COLOR, DISABLED_INNER_BORDER_COLOR)

      model.hasError ->
        Pair(ERROR_OUTER_BORDER_COLOR, ERROR_INNER_BORDER_COLOR)

      IJSwingUtilities.hasFocus(component) ->
        Pair(FOCUSED_OUTER_BORDER_COLOR, FOCUSED_INNER_BORDER_COLOR)

      model.hasPlaceHolder ->
        Pair(UIUtil.TRANSPARENT_COLOR, PLACEHOLDER_INNER_BORDER_COLOR)

      else ->
        Pair(UIUtil.TRANSPARENT_COLOR, INNER_BORDER_COLOR)
    }
    var adjustedInnerWidth = INNER_BORDER_WIDTH

    when (outerColor) {
      UIUtil.TRANSPARENT_COLOR -> rect.applyInset(OUTER_BORDER_WIDTH)
      innerColor -> adjustedInnerWidth += OUTER_BORDER_WIDTH
      else -> {
        drawRoundedRect(g2, rect, outerColor, OUTER_BORDER_WIDTH, cornerRadius + INNER_BORDER_WIDTH)
        rect.applyInset(OUTER_BORDER_WIDTH)
      }
    }

    drawRoundedRect(g2, rect, innerColor, adjustedInnerWidth, cornerRadius, component.background)
  }

  /**
   * Draw a rounded rectangle.
   * A border is drawn with rounded edges and the area is optionally filled with a background color.
   *
   * @param rect the bounds of the rectangle
   * @param borderColor the color of the border
   * @param stroke the width of the border
   * @param cornerRadius the radius of the inside edge of the rounded corner
   * @param backgroundColor the color to fill the area with. Use [UIUtil.TRANSPARENT_COLOR] to omit.
   */
  private fun drawRoundedRect(g2: Graphics2D,
                              rect: Rectangle2D.Float,
                              borderColor: Color,
                              stroke: Float,
                              cornerRadius: Float,
                              backgroundColor: Color = UIUtil.TRANSPARENT_COLOR) {
    val inset = INNER_BORDER_WIDTH + OUTER_BORDER_WIDTH
    val builder = PathBuilder(rect, stroke, cornerRadius, paddingTop.toFloat(), paddingBottom.toFloat(), inset + paddingLeft, inset + paddingRight)

    if (backgroundColor != UIUtil.TRANSPARENT_COLOR) {
      g2.color = backgroundColor
      g2.fill(builder.makeLeftMargin())
      g2.fill(builder.makeRightMargin())
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
      if (paddingTop > 0) {
        g2.fill(builder.makeTopMargin())
      }
      if (paddingBottom > 0) {
        g2.fill(builder.makeBottomMargin())
      }
    }

    g2.color = borderColor
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.stroke = BasicStroke(stroke)
    g2.draw(builder.makeRoundedRect())
  }

  private class PathBuilder(rect: Rectangle2D.Float,
                            stroke: Float,
                            cornerRadius: Float,
                            private val marginTop: Float,
                            private val marginBottom: Float,
                            private val marginLeft: Float,
                            private val marginRight: Float) {

    private val left: Float
    private val top: Float
    private val right: Float
    private val bottom: Float
    private val corner: Float
    private val curve1: Float
    private val curve2: Float

    init {
      rect.applyInset(stroke / 2f)
      left = rect.x
      top = rect.y
      right = rect.x + rect.width
      bottom = rect.y + rect.height
      corner = cornerRadius + stroke / 2f
      curve1 = cornerRadius / 2f
      curve2 = cornerRadius - cornerRadius * Math.sqrt(3.0).toFloat() / 2f
      rect.applyInset(-stroke / 2f)
    }

    fun makeRoundedRect(): Shape {
      val path = Path2D.Float()
      addLeftSideCurve(path)
      path.lineTo(right - corner, top)
      addRightSideCurve(path)
      path.lineTo(left + corner, bottom)
      return path
    }

    fun makeLeftMargin(): Shape {
      val path = Path2D.Float()
      addLeftSideCurve(path)
      path.lineTo(marginLeft, top)
      path.lineTo(marginLeft, bottom)
      path.lineTo(left + corner, bottom)
      return path
    }

    fun makeRightMargin(): Shape {
      val path = Path2D.Float()
      addRightSideCurve(path)
      path.lineTo(right - marginRight, bottom)
      path.lineTo(right - marginRight, top)
      path.lineTo(right - corner, top)
      return path
    }

    fun makeTopMargin(): Shape {
      val path = Path2D.Float()
      path.moveTo(left + corner, top)
      path.lineTo(right - corner, top)
      path.lineTo(right - corner, top + marginTop + 1f)
      path.lineTo(left + corner, top + marginTop + 1f)
      return path
    }

    fun makeBottomMargin(): Shape {
      val path = Path2D.Float()
      path.moveTo(left + corner, bottom)
      path.lineTo(right - corner, bottom)
      path.lineTo(right - corner, bottom - marginBottom - 1f)
      path.lineTo(left + corner, bottom - marginBottom - 1f)
      return path
    }

    private fun addLeftSideCurve(path: Path2D.Float) {
      path.moveTo(left + corner, bottom)
      path.curveTo(left + curve1, bottom - curve2, left + curve2, bottom - curve1, left, bottom - corner)
      path.lineTo(left, top + corner)
      path.curveTo(left + curve2, top + curve1, left + curve1, top + curve2, left + corner, top)
    }

    private fun addRightSideCurve(path: Path2D.Float) {
      path.moveTo(right - corner, top)
      path.curveTo(right - curve1, top + curve2, right - curve2, top + curve1, right, top + corner)
      path.lineTo(right, bottom - corner)
      path.curveTo(right - curve2, bottom - curve1, right - curve1, bottom - curve2, right - corner, bottom)
    }
  }
}
