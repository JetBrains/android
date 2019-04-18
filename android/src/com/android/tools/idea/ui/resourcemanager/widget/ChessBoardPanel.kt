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
package com.android.tools.idea.ui.resourcemanager.widget

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.Paint
import java.awt.Rectangle
import java.awt.TexturePaint
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.reflect.KProperty

// Chessboard texture constants
/**
 * Size of a single square in the chessboard pattern
 */
private val CHESSBOARD_CELL_SIZE = JBUI.scale(10)

/**
 * Size of the whole patten (width and height)
 */
private val CHESSBOARD_PATTERN_SIZE = 2 * CHESSBOARD_CELL_SIZE

private val CHESSBOARD_COLOR_1 = JBColor(0xCDCDCD, 0x414243)

private val CHESSBOARD_COLOR_2 = JBColor(0xF1F1F1, 0x393A3B)

private val TEXTURE_ANCHOR = Rectangle(0, 0, CHESSBOARD_PATTERN_SIZE,
                                       CHESSBOARD_PATTERN_SIZE)

/**
 * A [TexturePaint] that updates itself when the theme changes
 */
private val CHESSBOARD_PAINT by object {

  private var isDarcula = UIUtil.isUnderDarcula()
  private var chessboardPaint = TexturePaint(createTexturePattern(), TEXTURE_ANCHOR)

  /**
   * Four alternating squares to make the chessboard pattern
   */
  private fun createTexturePattern() = UIUtil.createImage(CHESSBOARD_PATTERN_SIZE,
                                                          CHESSBOARD_PATTERN_SIZE,
                                                          BufferedImage.TYPE_INT_ARGB).apply {
    with(this.graphics) {
      color = CHESSBOARD_COLOR_1
      fillRect(0, 0, CHESSBOARD_PATTERN_SIZE,
               CHESSBOARD_PATTERN_SIZE)
      color = CHESSBOARD_COLOR_2
      fillRect(0, CHESSBOARD_CELL_SIZE,
               CHESSBOARD_CELL_SIZE,
               CHESSBOARD_CELL_SIZE)
      fillRect(CHESSBOARD_CELL_SIZE, 0,
               CHESSBOARD_CELL_SIZE,
               CHESSBOARD_CELL_SIZE)
    }
  }

  operator fun getValue(thisRef: Any?, property: KProperty<*>): Paint {
    if (isDarcula != UIUtil.isUnderDarcula()) {
      isDarcula = UIUtil.isUnderDarcula()
      chessboardPaint = TexturePaint(createTexturePattern(), TEXTURE_ANCHOR)
    }
    return chessboardPaint
  }
}

/**
 * A [JPanel] which draws a chessboard pattern as its background when [showChessboard] is true.
 */
class ChessBoardPanel(
  layoutManager: LayoutManager = FlowLayout())
  : JPanel(layoutManager) {

  var showChessboard: Boolean = true

  init {
    isOpaque = false
  }

  override fun paintComponent(g: Graphics?) {
    if (showChessboard) {
      with(g as Graphics2D) {
        val oldPaint = paint
        paint = CHESSBOARD_PAINT
        val insets = insets
        fillRect(insets.left,
                 insets.top,
                 size.width - insets.right - insets.left,
                 size.height - insets.bottom - insets.top)
        paint = oldPaint
      }
    }
    super.paintComponent(g)
  }
}