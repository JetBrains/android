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
package com.android.tools.idea.npw.assetstudio.ui

import com.android.ide.common.vectordrawable.VdIcon
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * [TableCellRenderer] used in [IconPickerDialog], uses a [JBLabel] to render the icons used in the picker with the correct LaF.
 *
 * This CellRenderer expects [VectorDrawableIcon][VdIcon]s in the [JTable] model.
 */
class IconPickerCellRenderer : TableCellRenderer {

  private val label = IconPickerCellComponent()

  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    return label.apply { updateComponent(isSelected = isSelected, isFocused = hasFocus, value = value) }
  }
}

private const val ARC_SIZE = 5
private const val BORDER_SIZE = 1

private class IconPickerCellComponent : JBLabel() {
  private val backgroundFocusedColor = JBColor(Color(0x1a1886f7.toInt(), true), Color(0x1a9ccdff.toInt(), true))

  private val selectedFocusedBorder = IdeBorderFactory.createRoundedBorder(JBUI.scale(ARC_SIZE), JBUI.scale(BORDER_SIZE))

  init {
    isOpaque = false
    border = JBUI.Borders.empty()
    font = JBUI.Fonts.miniFont()
  }

  private var isSelected: Boolean = false

  private var isFocused: Boolean = false

  fun updateComponent(isSelected: Boolean, isFocused: Boolean, value: Any?) {
    var displayName = ""
    if (value is VdIcon) {
      this.isSelected = isSelected
      this.isFocused = isFocused
      icon = value
      displayName = value.displayName
    } else {
      this.isSelected = false
      this.isFocused = false
      icon = null
    }
    text = ""
    AccessibleContextUtil.setName(this, displayName)
  }

  override fun paintComponent(g: Graphics?) {
    if (isFocused || isSelected) {
      if (g is Graphics2D) {
        val oldAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = backgroundFocusedColor
        val offset = JBUI.scale(BORDER_SIZE)
        g.fillRoundRect(0, 0, width - offset, height - offset, JBUI.scale(ARC_SIZE), JBUI.scale(ARC_SIZE))

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
        if (isFocused) {
          selectedFocusedBorder.apply {
            setColor(UIUtil.getTreeSelectionBackground(isFocused))
            paintBorder(this@IconPickerCellComponent, g, 0, 0, width, height)
          }
        }
      }
    }
    super.paintComponent(g)
  }
}