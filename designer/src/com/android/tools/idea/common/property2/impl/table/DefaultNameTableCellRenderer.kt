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
package com.android.tools.idea.common.property2.impl.table

import com.android.tools.adtui.ptable2.*
import com.android.tools.idea.common.property2.api.PropertyItem
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.JComponent
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
    val property = item as PropertyItem
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    myBorder = JBUI.Borders.empty()
    var indent = standardIndent + depth * depthIndent
    when {
      item is PTableGroupItem -> {
        icon = UIUtil.getTreeNodeIcon(table.isExpanded(item), isSelected, hasFocus)
        iconTextGap = max(iconWidth - icon.iconWidth, minSpacing)
      }
      property.namespaceIcon != null -> {
        icon = property.namespaceIcon
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
}
