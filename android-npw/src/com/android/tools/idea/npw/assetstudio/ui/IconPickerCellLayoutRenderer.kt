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
package com.android.tools.idea.npw.assetstudio.ui

import com.android.resources.ResourceType
import com.android.tools.idea.npw.assetstudio.assets.MaterialSymbolsVirtualFile
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.rendering.SlowResourcePreviewManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * [TableCellRenderer] used in [IconPickerDialog], uses a [JBLabel] to render the icons used in the
 * picker with the correct Look and Feel.
 *
 * This CellRenderer expects [MaterialSymbolsVirtualFile]s in the [JTable] model.
 */
class IconPickerCellLayoutRenderer(
  private val slowResourcePreviewManager: SlowResourcePreviewManager
) : TableCellRenderer {

  private val label = IconPickerCellComponentXML()

  override fun getTableCellRendererComponent(
    table: JTable?,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    if (table == null) {
      return JBLabel()
    }
    return label.apply {
      updateComponent(
        resourcePreviewManager = slowResourcePreviewManager,
        table = table,
        isSelected = isSelected,
        isFocused = hasFocus,
        value = value,
        row = row,
        column = column,
      )
    }
  }
}

private const val ARC_SIZE = 5
private const val BORDER_SIZE = 1
private const val TEXT_HEIGHT = 16

private class IconPickerCellComponentXML : JBLabel() {
  /** Background color for selected icons */
  private val backgroundFocusedColor = JBColor(Color(0x1a1886f7, true), Color(0x1a9ccdff, true))

  private val selectedFocusedBorder =
    IdeBorderFactory.createRoundedBorder(JBUI.scale(ARC_SIZE), JBUI.scale(BORDER_SIZE))

  init {
    isOpaque = false
    border = JBUI.Borders.empty()
    font = JBUI.Fonts.miniFont()

    horizontalTextPosition = CENTER
    verticalTextPosition = BOTTOM
    horizontalAlignment = CENTER
  }

  private var isSelected: Boolean = false

  private var isFocused: Boolean = false

  fun updateComponent(
    resourcePreviewManager: SlowResourcePreviewManager,
    table: JTable,
    isSelected: Boolean,
    isFocused: Boolean,
    value: Any?,
    row: Int,
    column: Int,
  ) {
    val cellRect = table.getCellRect(row, column, false)
    val dimension = Dimension(cellRect.width, cellRect.height - TEXT_HEIGHT)
    val iconCallback = { file: VirtualFile, dimension: Dimension ->
      resourcePreviewManager.getIcon(
        DesignAsset(file, listOf(), ResourceType.LAYOUT),
        dimension.width,
        dimension.height,
        table,
        { table.getCellRect(row, column, false).let(table::repaint) },
        { table.visibleRect.intersects(table.getCellRect(row, column, false)) },
      )
    }
    var displayName = ""
    if (value is MaterialSymbolsVirtualFile) {
      this.isSelected = isSelected
      this.isFocused = isFocused
      icon = iconCallback(value, dimension)
      displayName = value.displayName
    } else {
      this.isSelected = false
      this.isFocused = false
      icon = null
    }
    text = displayName
    AccessibleContextUtil.setName(this, displayName)
  }

  override fun paintComponent(g: Graphics?) {
    if (isFocused || isSelected) {
      if (g is Graphics2D) {
        val oldAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = backgroundFocusedColor
        val offset = JBUI.scale(BORDER_SIZE)
        g.fillRoundRect(
          0,
          0,
          width - offset,
          height - offset,
          JBUI.scale(ARC_SIZE),
          JBUI.scale(ARC_SIZE),
        )

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
        if (isFocused) {
          selectedFocusedBorder.apply {
            setColor(UIUtil.getTreeSelectionBackground(isFocused))
            paintBorder(this@IconPickerCellComponentXML, g, 0, 0, width, height)
          }
        }
      }
    }
    super.paintComponent(g)
  }
}
