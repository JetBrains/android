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

import com.android.tools.adtui.common.border
import com.android.tools.adtui.common.secondaryPanelBackground
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*
import kotlin.properties.Delegates
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

private val TEXTURE_ANCHOR = Rectangle(0, 0, CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE)

/**
 * A [TexturePaint] that updates itself when the theme changes
 */
private val CHESSBOARD_PAINT by object {

  private var isDarcula = UIUtil.isUnderDarcula()
  private var chessboardPaint = TexturePaint(createTexturePattern(), TEXTURE_ANCHOR)

  /**
   * Four alternating squares to make the chessboard pattern
   */
  private fun createTexturePattern() = UIUtil.createImage(CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE,
                                                          BufferedImage.TYPE_INT_ARGB).apply {
    with(this.graphics) {
      color = CHESSBOARD_COLOR_1
      fillRect(0, 0, CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE)
      color = CHESSBOARD_COLOR_2
      fillRect(0, CHESSBOARD_CELL_SIZE, CHESSBOARD_CELL_SIZE, CHESSBOARD_CELL_SIZE)
      fillRect(CHESSBOARD_CELL_SIZE, 0, CHESSBOARD_CELL_SIZE, CHESSBOARD_CELL_SIZE)
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

// Graphic constant for the view

/**
 * Ratio of the height on the width of the whole view.
 * These values come from the UI specs.
 */
private const val VIEW_HEIGHT_WIDTH_RATIO = 26 / 23f

/**
 * Ratio of the height on the width of the thumbnail
 * These values come from the UI specs.
 */
private const val THUMBNAIL_HEIGHT_WIDTH_RATIO = 115 / 130f

private val LARGE_MAIN_CELL_BORDER_SELECTED = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(10),
  RoundedLineBorder(UIUtil.getTreeSelectionBackground(), 4, 2)
)

private val LARGE_MAIN_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(11),
  RoundedLineBorder(border, 4, 1)
)

private val BOTTOM_PANEL_BORDER = JBUI.Borders.empty(10, 8, 10, 10)

private val PRIMARY_FONT_SIZE = JBUI.scaleFontSize(12f).toFloat()

private val SECONDARY_FONT_SIZE = JBUI.scaleFontSize(10f).toFloat()

private const val DEFAULT_WIDTH = 120

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
   * The size of the [thumbnail] container that should be used to compute the size of the thumbnail component
   */
  val thumbnailSize: Dimension get() = contentWrapper.preferredSize

  /**
   * Set the width of the whole view. The height is computed using a fixed ratio of [VIEW_HEIGHT_WIDTH_RATIO].
   */
  var viewWidth by Delegates.observable(DEFAULT_WIDTH) { _, _, newValue ->
    contentWrapper.preferredSize = Dimension(newValue, (newValue * THUMBNAIL_HEIGHT_WIDTH_RATIO).toInt())
    preferredSize = Dimension(newValue, (newValue * VIEW_HEIGHT_WIDTH_RATIO).toInt())
  }

  /**
   * Set the title label of this card
   */
  var title: String by Delegates.observable("") { _, _, newValue -> titleLabel.text = newValue }

  /**
   * Set the subtitle label of this card
   */
  var subtitle: String by Delegates.observable("") { _, _, newValue -> secondLineLabel.text = newValue }

  /**
   * Set the subtitle label of this card
   */
  var metadata: String by Delegates.observable("") { _, _, newValue -> thirdLineLabel.text = newValue }

  private val bottomPanel = JPanel(BorderLayout(2, 8)).apply {
    background = secondaryPanelBackground
    isOpaque = true
    border = BOTTOM_PANEL_BORDER
  }

  private val titleLabel = JLabel().apply {
    font = font.deriveFont(Font.BOLD, PRIMARY_FONT_SIZE)
  }

  private val secondLineLabel = JLabel().apply {
    font = font.deriveFont(SECONDARY_FONT_SIZE)
  }

  private val thirdLineLabel = JLabel().apply {
    font = font.deriveFont(SECONDARY_FONT_SIZE)
  }

  var selected by Delegates.observable(false) { _, _, selected ->
    border = if (selected) LARGE_MAIN_CELL_BORDER_SELECTED else LARGE_MAIN_CELL_BORDER
  }

  private var contentWrapper = object : JPanel(BorderLayout()) {
    init {
      isOpaque = false
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
      add(titleLabel, BorderLayout.NORTH)
      add(Box.createVerticalBox().apply {
        add(secondLineLabel)
        add(thirdLineLabel)
      })
      add(JLabel(StudioIcons.Common.WARNING), BorderLayout.EAST)
    }
  }
}
