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

import com.android.tools.adtui.stdui.StandardColors.BACKGROUND_COLOR
import com.android.tools.adtui.stdui.StandardColors.DISABLED_INNER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.ERROR_INNER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.ERROR_OUTER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.FOCUSED_INNER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.FOCUSED_OUTER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.INNER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.PLACEHOLDER_INNER_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardDimensions.INNER_BORDER_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.OUTER_BORDER_WIDTH
import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
import com.android.tools.adtui.stdui.StandardDimensions.VERTICAL_PADDING
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.TRANSPARENT_COLOR
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.border.Border
import javax.swing.plaf.UIResource

/**
 * A standard border with rounded edges.
 * Two rounded edges are drawn and the area is optionally filled with a background color.
 * In addition an optional left diagonal and a right vertical line may be specified (for a ComboBox).
 *
 * @param cornerRadius the corner radius of each of the 4 corners
 */
class StandardBorder(private val cornerRadius: Float) : Border, UIResource {

  override fun getBorderInsets(c: Component?): Insets {
    val insets = JBUI.insets(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING)
    val inset = Math.round(INNER_BORDER_WIDTH + OUTER_BORDER_WIDTH)
    insets.left += inset
    insets.right += inset
    insets.top += inset
    insets.bottom += inset
    return insets
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }

  override fun paintBorder(component: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    // Do not paint anything.
    // Wait until the UI is calling the paintBorder method below.
    // This border will paint the background as well as the border.
  }

  fun paintBorder(component: Component, g: Graphics, hasErrors: Boolean, hasFocus: Boolean, hasVisiblePlaceHolder: Boolean) {
    val g2 = g as Graphics2D
    val rect = Rectangle2D.Float(0f, 0f, component.width.toFloat(), component.height.toFloat())
    val (outerColor, innerColor) = when {
      !component.isEnabled -> TRANSPARENT_COLOR to DISABLED_INNER_BORDER_COLOR
      hasErrors -> ERROR_OUTER_BORDER_COLOR to ERROR_INNER_BORDER_COLOR
      hasFocus -> FOCUSED_OUTER_BORDER_COLOR to FOCUSED_INNER_BORDER_COLOR
      hasVisiblePlaceHolder -> TRANSPARENT_COLOR to PLACEHOLDER_INNER_BORDER_COLOR
      else -> TRANSPARENT_COLOR to INNER_BORDER_COLOR
    }
    var adjustedInnerWidth = INNER_BORDER_WIDTH

    when (outerColor) {
      TRANSPARENT_COLOR -> rect.applyInset(OUTER_BORDER_WIDTH)
      innerColor -> adjustedInnerWidth += OUTER_BORDER_WIDTH
      else -> {
        drawRoundedRect(g2, rect, outerColor, OUTER_BORDER_WIDTH, cornerRadius + INNER_BORDER_WIDTH, TRANSPARENT_COLOR)
        rect.applyInset(OUTER_BORDER_WIDTH)
      }
    }

    drawRoundedRect(g2, rect, innerColor, adjustedInnerWidth, cornerRadius, BACKGROUND_COLOR)
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
  private fun drawRoundedRect(g2: Graphics2D, rect: Rectangle2D.Float, borderColor: Color, stroke: Float, cornerRadius: Float,
                              backgroundColor: Color = TRANSPARENT_COLOR) {
    rect.applyInset(stroke / 2f)
    val left = rect.x
    val top = rect.y
    val right = rect.x + rect.width
    val bottom = rect.y + rect.height
    val corner = cornerRadius + stroke / 2f
    val curve1 = cornerRadius / 2f
    val curve2 = cornerRadius - cornerRadius * Math.sqrt(3.0).toFloat() / 2f
    rect.applyInset(-stroke / 2f)

    val border = Path2D.Float()
    border.moveTo(left + corner, top)
    border.lineTo(right - corner, top)
    border.curveTo(right - curve1, top + curve2, right - curve2, top + curve1, right, top + corner)
    border.lineTo(right, bottom - corner)
    border.curveTo(right - curve2, bottom - curve1, right - curve1, bottom - curve2, right - corner, bottom)
    border.lineTo(left + corner, bottom)
    border.curveTo(left + curve1, bottom - curve2, left + curve2, bottom - curve1, left, bottom - corner)
    border.lineTo(left, top + corner)
    border.curveTo(left + curve2, top + curve1, left + curve1, top + curve2, left + corner, top)

    g2.setColorAndAlpha(borderColor)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.stroke = BasicStroke(stroke)
    g2.draw(border)

    if (backgroundColor != TRANSPARENT_COLOR) {
      g2.setColorAndAlpha(backgroundColor)
      g2.fill(border)
    }
  }
}
