/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.colorpicker2.internal

import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * The border of color block which provides the constraint to background color.
 */
private val COLOR_BUTTON_INNER_BORDER_COLOR = JBColor(Color(0, 0, 0, 26), Color(255, 255, 255, 26))

/** A focusable JButton to represent a color value. With the right LaF for the color picker. */
class ColorButton(var color: Color = Color.WHITE): JButton() {

  private val FOCUS_BORDER_WIDTH = JBUI.scale(3)
  private val ROUND_CORNER_ARC = JBUI.scale(5)

  enum class Status { NORMAL, HOVER, PRESSED }

  var status = Status.NORMAL

  init {
    preferredSize = JBUI.size(34)
    border = JBUI.Borders.empty(6)
    isRolloverEnabled = true
    hideActionText = true
    background = PICKER_BACKGROUND_COLOR

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        status = Status.HOVER
        repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        status = Status.NORMAL
        repaint()
      }

      override fun mousePressed(e: MouseEvent) {
        status = Status.PRESSED
        repaint()
      }

      override fun mouseReleased(e: MouseEvent) {
        status = when (status) {
          Status.PRESSED -> Status.HOVER
          else -> Status.NORMAL
        }
        repaint()
      }
    })

    with (getInputMap(JComponent.WHEN_FOCUSED)) {
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed")
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    }
  }

  override fun paintComponent(g: Graphics) {
    val g2d = g as Graphics2D
    val originalAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val originalColor = g.color

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    // Cleanup background
    g.color = background
    g.fillRect(0, 0, width, height)


    if (status == Status.HOVER || status == Status.PRESSED) {
      val l = insets.left / 2
      val t = insets.top / 2
      val w = width - l - insets.right / 2
      val h = height - t - insets.bottom / 2

      val focusColor = UIUtil.getFocusedBoundsColor()
      g.color = if (status == Status.HOVER) focusColor else focusColor.darker()
      g2d.fillRoundRect(l, t, w, h, 7, 7)
    }
    else if (isFocusOwner) {
      val l = insets.left - FOCUS_BORDER_WIDTH
      val t = insets.top - FOCUS_BORDER_WIDTH
      val w = width - l - insets.right + FOCUS_BORDER_WIDTH
      val h = height - t - insets.bottom + FOCUS_BORDER_WIDTH

      g.color = UIUtil.getFocusedFillColor()
      g2d.fillRoundRect(l, t, w, h, 7, 7)
    }

    val left = insets.left
    val top = insets.top
    val brickWidth = width - insets.left - insets.right
    val brickHeight = height - insets.top - insets.bottom
    g.color = color
    g2d.fillRoundRect(left, top, brickWidth, brickHeight, ROUND_CORNER_ARC, ROUND_CORNER_ARC)
    g.color = COLOR_BUTTON_INNER_BORDER_COLOR
    g2d.drawRoundRect(left, top, brickWidth, brickHeight, ROUND_CORNER_ARC, ROUND_CORNER_ARC)

    g.color = originalColor
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing)
  }
}