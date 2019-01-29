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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.stdui.StandardColors.ERROR_BUBBLE_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.ERROR_BUBBLE_FILL_COLOR
import com.android.tools.adtui.stdui.StandardColors.ERROR_BUBBLE_TEXT_COLOR
import com.android.tools.idea.common.property2.api.PropertyItem
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

private const val ICON_SPACING = 6
private const val MAX_COLUMN_WIDTH = 50 // Measured in 'em'

/**
 * Implementation of custom tool tips for displaying errors and warnings.
 *
 * This implementation only works when running in the IDE, since we are using
 * the IJ IdeTooltipManager.
 */
class PropertyTooltip : IdeTooltip(null, Point(), TooltipComponent()) {
  private val tip = tipComponent as TooltipComponent

  private fun reset() {
    component = null
  }

  override fun onHidden() {
    reset()
  }

  companion object {

    fun setToolTip(component: JComponent, event: MouseEvent, property: PropertyItem, forValue: Boolean, text: String?): String? {
      val manager = IdeTooltipManager.getInstance()
      val tooltip = createToolTip(property, forValue, text.orEmpty())
      tooltip?.component = component
      tooltip?.point = event.point
      manager.setCustomTooltip(component, tooltip)
      return null
    }

    private fun createToolTip(property: PropertyItem, forValue: Boolean, currentText: String): PropertyTooltip? {
      if (!forValue) {
        val text = property.tooltipForName.nullize() ?: return null
        return createTooltipWithContent(text)
      }

      val validation = property.editingSupport.validation(currentText)
      when (validation.first) {
        EditingErrorCategory.ERROR ->
          return createTooltipWithContent(validation.second, StudioIcons.Common.ERROR_INLINE,
                                          ERROR_BUBBLE_BORDER_COLOR, ERROR_BUBBLE_TEXT_COLOR, ERROR_BUBBLE_FILL_COLOR)
        EditingErrorCategory.WARNING ->
          return createTooltipWithContent(validation.second, StudioIcons.Common.WARNING_INLINE)
        else -> {
          val text = property.tooltipForValue.nullize() ?: return null
          return createTooltipWithContent(text, null, null, null, null)
        }
      }
    }

    private fun createTooltipWithContent(text: String,
                                         icon: Icon? = null,
                                         border: Color? = null,
                                         foreground: Color? = null,
                                         background: Color? = null): PropertyTooltip {
      val tooltip = PropertyTooltip()
      tooltip.tip.icon = icon
      tooltip.tip.text = text
      tooltip.borderColor = border
      tooltip.textBackground = background
      tooltip.textForeground = foreground

      // Make the popup display faster if there is an error or warning,
      // use the normal delay if this is just information.
      tooltip.setHighlighterType(icon != null)
      return tooltip
    }
  }
}

private class TooltipComponent: JPanel(BorderLayout()) {
  private val iconLabel = JBLabel()
  private val textArea = JTextArea(0, 40)

  var icon: Icon?
    get() = iconLabel.icon
    set(value) {
      iconLabel.icon = value
      iconLabel.isVisible = (value != null)
    }

  var text: String
    get() = textArea.text
    set(value) {
      val forceUpdate = textArea.text != value
      textArea.text = value
      if (forceUpdate) {
        updateRowsAndColumns(value)
      }
    }

  init {
    background = UIUtil.TRANSPARENT_COLOR
    textArea.border = JBUI.Borders.empty()
    textArea.background = UIUtil.TRANSPARENT_COLOR
    textArea.lineWrap = true
    textArea.wrapStyleWord = true
    textArea.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    iconLabel.border = JBUI.Borders.emptyRight(ICON_SPACING)
    iconLabel.verticalAlignment = SwingConstants.TOP
    add(iconLabel, BorderLayout.WEST)
    add(textArea, BorderLayout.CENTER)
  }

  private fun updateRowsAndColumns(value: String) {
    val lines = value.lines()
    val fm = textArea.getFontMetrics(textArea.font)
    val em = fm.charWidth('m')
    val maxLineLength = lines.map { fm.stringWidth(it) }.max()
    val columns = if (maxLineLength != null) maxLineLength / em else Int.MAX_VALUE
    if (columns > MAX_COLUMN_WIDTH) {
      textArea.columns = MAX_COLUMN_WIDTH
      textArea.rows = 0
      forcePreferredSizeUpdate()
    }
    else {
      textArea.columns = columns + 1
      textArea.rows = lines.size
    }
  }

  // Hack
  // The lines are too wide, and word wrap is needed.
  // We will specify the number of columns but not the number of rows.
  // Unfortunately the correct number of rows is not computed correctly in JTextArea before
  // the first paint.
  // That makes these tooltips show up with 1 row the first time it is displayed.
  // To fix this: make a dummy paint when the text is replaced to force the internals of the
  // UI to compute the correct number of rows.
  private fun forcePreferredSizeUpdate() {
    val buffer = UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_RGB)
    val graphics = buffer.createGraphics()
    val size = textArea.preferredSize
    textArea.setBounds(0, 0, size.width, size.height)
    textArea.paint(graphics)
    graphics.dispose()
  }
}
