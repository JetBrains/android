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
package com.android.tools.property.panel.impl.table

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.ui.PropertyTooltip
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellRenderer
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableGroupItem
import com.android.tools.property.ptable2.PTableItem
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable
import kotlin.math.max

const val LEFT_STANDARD_INDENT = 2
const val MIN_SPACING = 2
const val DEPTH_INDENT = 8

/**
 * Table cell renderer for the name of a [PTableItem].
 */
class DefaultNameTableCellRenderer : SimpleColoredComponent(), PTableCellRenderer {
  private val standardIndent = JBUI.scale(LEFT_STANDARD_INDENT)
  private val minSpacing = JBUI.scale(MIN_SPACING)
  private val depthIndent = JBUI.scale(DEPTH_INDENT)
  private val iconWidth = UIUtil.getTreeCollapsedIcon().iconWidth

  override fun getEditorComponent(table: PTable, item: PTableItem, column: PTableColumn, depth: Int,
                                  isSelected: Boolean, hasFocus: Boolean): JComponent {
    clear()
    append(item.name)
    setPaintFocusBorder(false)
    setFocusBorderAroundIcon(true)
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    myBorder = JBUI.Borders.empty()
    var indent = standardIndent + depth * depthIndent
    when {
      item is PTableGroupItem -> {
        icon = UIUtil.getTreeNodeIcon(table.isExpanded(item), isSelected, hasFocus)
        iconTextGap = max(iconWidth - icon.iconWidth, minSpacing)
      }
      item is PropertyItem && item.namespaceIcon != null -> {
        icon = item.namespaceIcon?.let { if (isSelected && hasFocus) ColoredIconGenerator.generateWhiteIcon(it) else it }
        iconTextGap = max(iconWidth - icon.iconWidth, minSpacing)
      }
      else -> indent += iconWidth + minSpacing
    }
    if (isSelected && hasFocus) {
      foreground = UIUtil.getTreeSelectionForeground(true)
      background = UIUtil.getTreeSelectionBackground(true)
    }
    else {
      foreground = table.foregroundColor
      background = table.backgroundColor
    }
    ipad = Insets(0, indent, 0, 0)
    return this
  }

  override fun getToolTipText(event: MouseEvent): String? {
    // Trick: Use the component from the event.source for tooltip in tables. See TableEditor.getToolTip().
    val component = event.source as? JTable ?: return null
    val tableRow = component.rowAtPoint(event.point)
    val tableColumn = component.columnAtPoint(event.point)
    if (tableRow < 0 || tableColumn < 0) {
      return null
    }
    val item = component.getValueAt(tableRow, tableColumn)
    val property = item as? PropertyItem ?: return null
    return PropertyTooltip.setToolTip(component, event, property, forValue = tableColumn == 1, text = "")
  }
}
