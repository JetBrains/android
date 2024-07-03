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
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import java.awt.BorderLayout
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
private val CHESSBOARD_COLOR_1 = JBColor(0xCDCDCD, 0x414243)

private val CHESSBOARD_COLOR_2 = JBColor(0xF1F1F1, 0x393A3B)

private fun createTextureAnchor(scaledPatternSize: Int) = Rectangle(0, 0, scaledPatternSize, scaledPatternSize)

private fun createTexturePattern(scaledCellSize: Int): BufferedImage {
  val patternSize = scaledCellSize * 2
  return ImageUtil.createImage(patternSize, patternSize, BufferedImage.TYPE_INT_ARGB).apply {
    with(this.graphics) {
      color = CHESSBOARD_COLOR_1
      fillRect(0, 0, patternSize, patternSize)
      color = CHESSBOARD_COLOR_2
      fillRect(0, scaledCellSize, scaledCellSize, scaledCellSize)
      fillRect(scaledCellSize, 0, scaledCellSize, scaledCellSize)
      dispose()
    }
  }
}

/**
 * A [JPanel] which draws a chessboard pattern as its background when [showChessboard] is true.
 */
class ChessBoardPanel(
  cellSize: Int = 10,
  layoutManager: LayoutManager = BorderLayout())
  : JPanel(layoutManager) {

  /**
   * The Chess Board [Paint], it updates itself whenever the theme changes.
   */
  private val chessBoardPaint by object {
    private val scaledCellSize = JBUIScale.scale(cellSize)
    private val scaledPatternSize = scaledCellSize * 2

    private var isDarkTheme = !JBColor.isBright()
    private var paint = TexturePaint(createTexturePattern(scaledCellSize), createTextureAnchor(scaledPatternSize))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Paint {
      if (isDarkTheme == JBColor.isBright()) {
        isDarkTheme = !JBColor.isBright()
        paint = TexturePaint(createTexturePattern(scaledCellSize), createTextureAnchor(scaledPatternSize))
      }
      return paint
    }
  }

  var showChessboard: Boolean = true

  init {
    isOpaque = false
  }

  override fun paintComponent(g: Graphics?) {
    if (showChessboard) {
      with(g as Graphics2D) {
        val oldPaint = paint
        paint = chessBoardPaint
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