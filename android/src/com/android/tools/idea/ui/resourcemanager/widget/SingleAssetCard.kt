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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.border
import com.android.tools.adtui.common.secondaryPanelBackground
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.Border
import kotlin.properties.Delegates

// Graphic constant for the view

/**
 * Ratio of the height on the width of the thumbnail
 * These values come from the UI specs.
 */
private const val THUMBNAIL_HEIGHT_WIDTH_RATIO = 23 / 26f

private val LARGE_MAIN_CELL_BORDER_SELECTED get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(10),
  RoundedLineBorder(UIUtil.getTreeSelectionBackground(true), JBUI.scale(4), JBUI.scale(2))
)


private val LARGE_MAIN_CELL_BORDER_UNFOCUSED get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(10),
  RoundedLineBorder(UIUtil.getTreeSelectionBackground(false), JBUI.scale(4), JBUI.scale(2))
)

private var PREVIEW_BORDER_COLOR: Color = border

private val LARGE_MAIN_CELL_BORDER get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(11),
  RoundedLineBorder(PREVIEW_BORDER_COLOR, JBUI.scale(4), JBUI.scale(1))
)

private val ROW_CELL_BORDER get() = JBUI.Borders.empty(4)

private val ROW_CELL_BORDER_SELECTED get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(2),
  RoundedLineBorder(UIUtil.getTreeSelectionBackground(true), JBUI.scale(4), JBUI.scale(2))
)

private val ROW_CELL_BORDER_UNFOCUSED get() = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(2),
  RoundedLineBorder(UIUtil.getTreeSelectionBackground(false), JBUI.scale(4), JBUI.scale(2))
)

private val BOTTOM_PANEL_BORDER get()  = JBUI.Borders.empty(5, 8, 10, 10)

private val PRIMARY_FONT get() = StartupUiUtil.getLabelFont().deriveFont(mapOf(TextAttribute.WEIGHT to TextAttribute.WEIGHT_DEMIBOLD,
                                                                              TextAttribute.SIZE to JBUI.scaleFontSize(14f)))

private val SECONDARY_FONT_SIZE get() = JBUI.scaleFontSize(12f).toFloat()

private val SECONDARY_FONT_COLOR get() = JBColor(NamedColorUtil.getInactiveTextColor().darker(), NamedColorUtil.getInactiveTextColor())

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
    // When there's nothing to preview, SingleAssetCard and RowAssetView have different behaviors, so we let them deal with it.
    if (new == null) {
      setNonIconLayout()
    } else {
      setIconLayout()
    }
  }

  /**
   * The size of the [thumbnail] container that should be used to compute the size of the thumbnail component
   */
  val thumbnailSize: Dimension get() = contentWrapper.size

  /**
   * Set the width of the whole view.
   */
  var viewWidth: Int by Delegates.observable(DEFAULT_WIDTH) { _, _, newValue -> onViewWidthChanged(newValue) }

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

  protected val titleLabel = JBLabel().apply {
    font = PRIMARY_FONT
  }
  protected val secondLineLabel = JBLabel().apply {
    font = font.deriveFont(SECONDARY_FONT_SIZE)
    foreground = SECONDARY_FONT_COLOR
  }
  protected val thirdLineLabel = JBLabel().apply {
    font = font.deriveFont(SECONDARY_FONT_SIZE)
    foreground = SECONDARY_FONT_COLOR
  }

  abstract var selected: Boolean

  abstract var focused: Boolean

  protected var contentWrapper = ChessBoardPanel().apply {
    showChessboard = withChessboard
  }

  var issueLevel: IssueLevel by Delegates.observable(IssueLevel.NONE) { _, _, level -> issueIcon.icon = level.icon }

  protected val issueIcon = JBLabel(issueLevel.icon)

  var isNew: Boolean by Delegates.observable(false) { _, _, new -> newLabel.isVisible = new }

  protected val newLabel = object : JBLabel(" NEW ", SwingConstants.CENTER) {

    init {
      font = JBUI.Fonts.label(8f)
      foreground = JBColor.WHITE
      isVisible = isNew
    }

    override fun paintComponent(g: Graphics) {
      g.color = UIUtil.getTreeSelectionBorderColor()
      val antialias = (g as Graphics2D).getRenderingHint(RenderingHints.KEY_ANTIALIASING)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val insets = insets
      val descent = getFontMetrics(font).descent
      val height = height - insets.bottom - descent // Ensure that text is centered within the background
      g.fillRoundRect(insets.left, insets.top, width - insets.right, height, height, height)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias)
      super.paintComponent(g)
    }
  }

  /**
   * Called when [viewWidth] is changed
   */
  private fun onViewWidthChanged(width: Int) {
    val thumbnailSize = computeThumbnailSize(width)
    contentWrapper.preferredSize = thumbnailSize
    contentWrapper.size = thumbnailSize
    validate()
  }

  /**
   * Subclass implement this method to specify the size of the [thumbnail] giving the
   * desired [width]
   */
  protected abstract fun computeThumbnailSize(width: Int): Dimension

  protected abstract fun getBorder(selected: Boolean, focused: Boolean): Border

  /** Adjust layout when there is no icon to preview. */
  protected abstract fun setNonIconLayout()

  /** Adjust layout for when there's an icon to preview. */
  protected abstract fun setIconLayout()
}

/**
 * Component in the shape of a card with a large preview
 * and some textual info below.
 */
class SingleAssetCard : AssetView() {
  override var selected by Delegates.observable(false) { _, _, selected ->
    border = getBorder(selected, focused)
  }

  override var focused: Boolean by Delegates.observable(false) { _, _, focused ->
    border = getBorder(selected, focused)
  }

  override fun computeThumbnailSize(width: Int) = Dimension(width, (width * THUMBNAIL_HEIGHT_WIDTH_RATIO).toInt())

  private val bottomPanel = JPanel(BorderLayout(0, JBUI.scale(2))).apply {
    background = secondaryPanelBackground
    isOpaque = true
    border = BOTTOM_PANEL_BORDER
  }

  private val emptyLabel = JBLabel("Nothing to show", SwingConstants.CENTER).apply {
    foreground = AdtUiUtils.DEFAULT_FONT_COLOR
    font = JBUI.Fonts.label(10f)
  }

  init {
    isOpaque = false
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
    viewWidth = DEFAULT_WIDTH
  }

  override fun getBorder(selected: Boolean, focused: Boolean): Border = if (selected) {
    if (focused) LARGE_MAIN_CELL_BORDER_SELECTED else LARGE_MAIN_CELL_BORDER_UNFOCUSED
  }
  else LARGE_MAIN_CELL_BORDER

  override fun setIconLayout() {
    // No need to do anything.
  }

  override fun setNonIconLayout() {
    contentWrapper.removeAll()
    contentWrapper.add(emptyLabel)
  }
}

/**
 * Component in the shape of a card with a large preview
 * and some textual info below.
 */
class RowAssetView : AssetView() {

  private val CENTER_PANEL_BORDER_SELECTED = JBUI.Borders.empty(0, 10, 1, 1)

  override var selected by Delegates.observable(false) { _, _, selected ->
    border = getBorder(selected, focused)
    centerPanel.border = if (selected) CENTER_PANEL_BORDER_SELECTED else CENTER_PANEL_BORDER_UNSELECTED
  }

  override var focused: Boolean by Delegates.observable(false) { _, _, focused ->
    border = getBorder(selected, focused)
    centerPanel.border = if (selected) CENTER_PANEL_BORDER_SELECTED else CENTER_PANEL_BORDER_UNSELECTED
  }

  override fun getBorder(selected: Boolean, focused: Boolean): Border =
    if (selected) {
      if (focused) ROW_CELL_BORDER_SELECTED else ROW_CELL_BORDER_UNFOCUSED
    }
    else ROW_CELL_BORDER

  private val CENTER_PANEL_BORDER_UNSELECTED = BorderFactory.createCompoundBorder(
    JBUI.Borders.empty(0, 10, 0, 1),
    JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
  )

  private val centerPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = CENTER_PANEL_BORDER_UNSELECTED
  }

  private val metadataPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
    isOpaque = false
    add(secondLineLabel)
    add(Separator(4, 8))
    add(thirdLineLabel)
  }

  init {
    contentWrapper.border = JBUI.Borders.customLine(JBColor.border(), 1)
    viewWidth = DEFAULT_WIDTH
    background = UIUtil.getListBackground()
    with(centerPanel) {
      add(JPanel(BorderLayout()).apply {
        add(titleLabel)
        add(newLabel, BorderLayout.EAST)
        isOpaque = false
        border = JBUI.Borders.empty(8, 0, 0, 4)
      }, BorderLayout.NORTH)

      add(JPanel(BorderLayout()).apply {
        add(metadataPanel)
        add(issueIcon, BorderLayout.EAST)
        border = JBUI.Borders.empty(0, 0, 4, 4)
        isOpaque = false
      }, BorderLayout.SOUTH)

      issueIcon.preferredSize = Dimension(newLabel.preferredSize.width, issueIcon.preferredSize.height)
    }

    add(contentWrapper, BorderLayout.WEST)
    add(centerPanel)
  }

  override fun computeThumbnailSize(width: Int) = Dimension(width, width)

  override fun setNonIconLayout() {
    contentWrapper.isVisible = false
  }

  override fun setIconLayout() {
    contentWrapper.isVisible = true
  }
}

