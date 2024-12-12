/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.border

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.border.LineBorder

class RoundedCornerBorder(
  private val cornerRadius: Float,
  private val backgroundColor: Color,
) : LineBorder(null, 0, true) {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    with(g as Graphics2D) {
      val oldColor = color
      val oldStroke = stroke

      setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION,
        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY,
      )

      setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

      setRenderingHint(
        RenderingHints.KEY_STROKE_CONTROL,
        if (MacUIUtil.USE_QUARTZ) {
          RenderingHints.VALUE_STROKE_PURE
        } else {
          RenderingHints.VALUE_STROKE_NORMALIZE
        },
      )

      val arcSize = JBUIScale.scale(cornerRadius)
      color = backgroundColor
      val roundedRect =
        RoundRectangle2D.Float(
          x.toFloat(),
          y.toFloat(),
          width.toFloat(),
          height.toFloat(),
          arcSize,
          arcSize,
        )
      fill(roundedRect)

      // Restore the graphics' original state
      color = oldColor
      stroke = oldStroke
    }
  }

  override fun getBorderInsets(c: Component?) = JBUI.insets(4)
}