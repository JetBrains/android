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
package com.android.tools.idea.resourceExplorer.view

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.properties.Delegates

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

private val TEXTURE_ANCHOR = Rectangle(0, 0, CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE)

/**
 * Four alternating squares to make the chessboard pattern
 */
private val TEXTURE_PATTERN = UIUtil.createImage(CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE, BufferedImage.TYPE_INT_ARGB).apply {
  with(this.graphics) {
    color = CHESSBOARD_COLOR_1
    fillRect(0, 0, CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE)
    color = CHESSBOARD_COLOR_2
    fillRect(0, CHESSBOARD_CELL_SIZE, CHESSBOARD_CELL_SIZE, CHESSBOARD_CELL_SIZE)
    fillRect(CHESSBOARD_CELL_SIZE, 0, CHESSBOARD_CELL_SIZE, CHESSBOARD_CELL_SIZE)
  }
}
private val CHESSBOARD_PAINT = TexturePaint(TEXTURE_PATTERN, TEXTURE_ANCHOR)

// Graphic constant for the view

private val LINE_BORDER = JBUI.Borders.customLine(Gray.x41, 2)

private val LARGE_MAIN_CELL_BORDER = JBUI.Borders.empty(10, 30, 10, 30)

private val LARGE_CONTENT_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(10, 0, 10, 0),
  LINE_BORDER
)

private val SMALL_MAIN_CELL_BORDER = JBUI.Borders.empty(5, 15, 5, 15)

private val SMALL_CONTENT_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(5, 0, 5, 0),
  LINE_BORDER
)

/**
 * Component in the shape of a card with a large preview
 * and some textual info below.
 */
class SingleAssetCard : JPanel(BorderLayout()) {

  /**
   * If true, draw a chessboard as in background of [thumbnail]
   */
  var withChessboard = false

  /**
   * Set the [JComponent] acting as the thumbnail of the object represented (e.g an image or a color)
   */
  var thumbnail by Delegates.observable(null as JComponent?) { _, old, new ->
    if (old !== new) {
      contentWrapper.removeAll()
      if (new != null) {
        contentWrapper.add(new)
      }
    }
  }

  /**
   * The height of the [thumbnail] container that should be used to compute the height of the thumbnail component
   */
  val thumbnailHeight: Int get() = contentWrapper.height

  /**
   * The width of the [thumbnail] container that should be used to compute the width of the thumbnail component
   */
  val thumbnailWidth: Int get() = contentWrapper.width

  /**
   * Set the title label of this card
   */
  var title: String by Delegates.observable("") { _, _, newValue -> titleLabel.text = newValue }

  /**
   * Set the subtitle label of this card
   */
  var subtitle: String by Delegates.observable("") { _, _, newValue -> subtitleLabel.text = newValue }

  private val bottomPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
  }
  private val titleLabel = JLabel()

  private val subtitleLabel = JLabel()

  private var contentWrapper = object : JPanel(BorderLayout()) {
    init {
      isOpaque = false
      border = LARGE_CONTENT_CELL_BORDER
    }

    override fun paintComponent(g: Graphics?) {
      if (withChessboard) {
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

  init {
    border = LARGE_MAIN_CELL_BORDER
    add(contentWrapper)
    add(bottomPanel, BorderLayout.SOUTH)
    with(bottomPanel) {
      add(titleLabel)
      add(subtitleLabel, BorderLayout.SOUTH)
      add(JLabel(StudioIcons.Common.WARNING), BorderLayout.EAST)
    }
  }

  var useSmallMargins = false
    set(smallMargin) {
      field = smallMargin
      border = if (smallMargin) SMALL_MAIN_CELL_BORDER else LARGE_MAIN_CELL_BORDER
      contentWrapper.border = if (smallMargin) SMALL_CONTENT_CELL_BORDER else SMALL_CONTENT_CELL_BORDER
    }
}
