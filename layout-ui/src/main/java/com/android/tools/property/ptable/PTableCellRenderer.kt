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
package com.android.tools.property.ptable

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

interface PTableCellRenderer {

  /**
   * Returns component for rendering the [column] of the specified [item].
   *
   * The [depth] indicates how deeply nested the [item] should be shown in the [table].
   * If [isSelected] the item should be shown as selected.
   * If [hasFocus] the item should be shown with focus colors.
   * If [isExpanded] the item should be shown without ellipsis (the user is hovering over the item).
   *
   * A return value of null means the cell is empty.
   */
  fun getEditorComponent(table: PTable,
                         item: PTableItem,
                         column: PTableColumn,
                         depth: Int,
                         isSelected: Boolean,
                         hasFocus: Boolean,
                         isExpanded: Boolean): JComponent?
}

class DefaultPTableCellRenderer : SimpleColoredComponent(), PTableCellRenderer {

  override fun getEditorComponent(table: PTable,
                                  item: PTableItem,
                                  column: PTableColumn,
                                  depth: Int,
                                  isSelected: Boolean,
                                  hasFocus: Boolean,
                                  isExpanded: Boolean): JComponent? {
    clear()
    setPaintFocusBorder(hasFocus)
    font = table.activeFont
    if (isSelected && hasFocus) {
      foreground = UIUtil.getTreeForeground(true, true)
      background = UIUtil.getTreeSelectionBackground(true)
    }
    else {
      foreground = table.foregroundColor
      background = table.backgroundColor
    }
    @Suppress("LiftReturnOrAssignment")
    if (column == PTableColumn.NAME) {
      append(item.name)
      border = if (depth > 0) JBUI.Borders.emptyLeft(4 * depth) else null
    }
    else {
      append(item.value.orEmpty())
      border = JBUI.Borders.customLine(table.gridLineColor, 0, 1, 0, 0)
    }
    return this
  }
}

class DefaultPTableCellRendererProvider : PTableCellRendererProvider {
  val renderer = DefaultPTableCellRenderer()

  override fun invoke(table: PTable, property: PTableItem, column: PTableColumn): PTableCellRenderer {
    return renderer
  }
}
