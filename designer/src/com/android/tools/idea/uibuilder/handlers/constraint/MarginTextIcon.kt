/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/** Icon used by the [ConstraintLayoutHandler] to render the margin value in text. */
internal class MarginTextIcon(private val myText: String) : Icon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    g.color = JBColor.foreground()
    g.font = g.font.deriveFont(Font.PLAIN, JBUI.scaleFontSize(DEFAULT_ICON_FONT_SIZE).toFloat())
    val metrics = g.fontMetrics
    val strWidth = metrics.stringWidth(myText)
    (g as Graphics2D).setRenderingHint(
      RenderingHints.KEY_TEXT_ANTIALIASING,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
    )
    val stringY = (iconHeight - metrics.height) / 2 + metrics.ascent
    g.drawString(myText, x + (iconWidth - strWidth) / 2, y + stringY - 1)
    g.color = JBColor.foreground().darker()
    val marginRight = 6
    g.drawLine(x + 1, y + iconHeight - 1, x + iconWidth - 1, y + iconHeight - 1)
    g.drawLine(x + 1, y + iconHeight, x + 1, y + iconHeight - marginRight)
    g.drawLine(x + iconWidth - 1, y + iconHeight, x + iconWidth - 1, y + iconHeight - marginRight)
  }

  override fun getIconWidth(): Int = JBUI.scale(DEFAULT_ICON_WIDTH)

  override fun getIconHeight(): Int = JBUI.scale(DEFAULT_ICON_HEIGHT)

  companion object {
    private const val DEFAULT_ICON_FONT_SIZE = 12f
    private const val DEFAULT_ICON_WIDTH = 36
    private const val DEFAULT_ICON_HEIGHT = 16
  }
}
