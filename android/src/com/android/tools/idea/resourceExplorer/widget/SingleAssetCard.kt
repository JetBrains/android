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
package com.android.tools.idea.resourceExplorer.widget

import com.android.tools.adtui.common.border
import com.android.tools.adtui.common.secondaryPanelBackground
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.properties.Delegates

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

private var PREVIEW_BORDER_COLOR: Color = border

private val LARGE_MAIN_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(11),
  RoundedLineBorder(PREVIEW_BORDER_COLOR, 4, 1)
)

private val ROW_CELL_BORDER = JBUI.Borders.empty(4)

private val ROW_CELL_BORDER_SELECTED = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(2),
  RoundedLineBorder(UIUtil.getTreeSelectionBackground(), 4, 2)
)

private val BOTTOM_PANEL_BORDER = JBUI.Borders.empty(10, 8, 10, 10)

private val PRIMARY_FONT_SIZE = JBUI.scaleFontSize(12f).toFloat()

private val SECONDARY_FONT_SIZE = JBUI.scaleFontSize(10f).toFloat()

private const val DEFAULT_WIDTH = 120

enum class IssueLevel(internal val icon: Icon) {
  NONE(EmptyIcon.ICON_16),
  INFO(StudioIcons.Common.INFO),
  WARNING(StudioIcons.Common.WARNING),
  ERROR(StudioIcons.Common.ERROR)
}

/**
 * Abstract class to represent a graphical asset in the resource explorer.
 * This allows to set
 */
abstract class AssetView : JPanel(BorderLayout()) {

  /**
   * If true, draw a chessboard as in background of [thumbnail]
   */
  var withChessboard: Boolean by Delegates.observable(false) { _, _, withChessboard -> contentWrapper.showChessboard = withChessboard }

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
  abstract var viewWidth: Int

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

  protected val titleLabel = JLabel().apply {
    font = font.deriveFont(Font.BOLD, PRIMARY_FONT_SIZE)
  }
  protected val secondLineLabel = JLabel().apply {
    font = font.deriveFont(SECONDARY_FONT_SIZE)
  }
  protected val thirdLineLabel = JLabel().apply {
    font = font.deriveFont(SECONDARY_FONT_SIZE)
  }

  abstract var selected: Boolean

  protected var contentWrapper = ChessBoardPanel(BorderLayout()).apply { showChessboard = withChessboard }

  var issueLevel: IssueLevel by Delegates.observable(IssueLevel.NONE) { _, _, level -> issueIcon.icon = level.icon }

  protected val issueIcon = JLabel(issueLevel.icon)

  var isNew: Boolean by Delegates.observable(false) { _, _, new -> newLabel.isVisible = new }

  protected val newLabel = object : JLabel(" NEW ") {

    init {
      font = UIUtil.getLabelFont(UIUtil.FontSize.MINI)
      foreground = JBColor.WHITE
      isVisible = isNew
    }

    override fun paintComponent(g: Graphics) {
      g.color = UIUtil.getTreeSelectionBorderColor()
      val antialias = (g as Graphics2D).getRenderingHint(RenderingHints.KEY_ANTIALIASING)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.fillRoundRect(0, 0, width, height, height, height)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias)
      super.paintComponent(g)
    }
  }
}

/**
 * Component in the shape of a card with a large preview
 * and some textual info below.
 */
class SingleAssetCard : AssetView() {

  override var selected by Delegates.observable(false) { _, _, selected ->
    border = if (selected) LARGE_MAIN_CELL_BORDER_SELECTED else LARGE_MAIN_CELL_BORDER
  }

  override var viewWidth by Delegates.observable(DEFAULT_WIDTH) { _, _, newValue ->
    contentWrapper.preferredSize = Dimension(newValue, (newValue * THUMBNAIL_HEIGHT_WIDTH_RATIO).toInt())
    preferredSize = Dimension(newValue, (newValue * VIEW_HEIGHT_WIDTH_RATIO).toInt())
    maximumSize = preferredSize
    minimumSize = preferredSize
    validate()
  }

  private val bottomPanel = JPanel(BorderLayout(2, 8)).apply {
    background = secondaryPanelBackground
    isOpaque = true
    border = BOTTOM_PANEL_BORDER
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
      add(issueIcon, BorderLayout.EAST)
    }
  }
}

/**
 * Component in the shape of a card with a large preview
 * and some textual info below.
 */
class RowAssetView : AssetView() {

  override var selected by Delegates.observable(false) { _, _, selected ->
    border = if (selected) ROW_CELL_BORDER_SELECTED else ROW_CELL_BORDER
  }

  override var viewWidth by Delegates.observable(DEFAULT_WIDTH) { _, _, newValue ->
    contentWrapper.preferredSize = Dimension(newValue, newValue)
    validate()
  }

  private val centerPanel = JPanel(BorderLayout()).apply {
    isOpaque = true
    border = BorderFactory.createCompoundBorder(
      JBUI.Borders.empty(0, 10, 2, 1),
      JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    )
  }

  private val metadataPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
    add(secondLineLabel)
    add(separator())
    add(thirdLineLabel)
  }

  init {
    contentWrapper.border = JBUI.Borders.customLine(JBColor.border(), 1)

    with(centerPanel) {
      add(JPanel(BorderLayout()).apply {
        add(titleLabel)
        add(newLabel, BorderLayout.EAST)
        border = JBUI.Borders.empty(8, 0, 0, 4)
      }, BorderLayout.NORTH)

      add(JPanel(BorderLayout()).apply {
        add(metadataPanel)
        add(issueIcon, BorderLayout.EAST)
        border = JBUI.Borders.empty(0, 0, 8, 4)
      }, BorderLayout.SOUTH)

      issueIcon.preferredSize = Dimension(newLabel.preferredSize.width, issueIcon.preferredSize.height)
    }

    add(contentWrapper, BorderLayout.WEST)
    add(centerPanel)
  }
}

private fun separator() = object : JComponent() {

  private val lineWidth = JBUI.scale(1)

  init {
    background = PREVIEW_BORDER_COLOR
    border = JBUI.Borders.empty(0, 4)
  }

  override fun paint(g: Graphics) {
    g.color = background
    val insets = insets
    g.fillRect(insets.left, insets.top, lineWidth, height - insets.top - insets.bottom)
  }

  override fun getPreferredSize(): Dimension {
    val insets = insets
    val width = lineWidth + insets.left + insets.right
    val height = parent.height - insets.top + insets.bottom
    return Dimension(width, height)
  }
}
