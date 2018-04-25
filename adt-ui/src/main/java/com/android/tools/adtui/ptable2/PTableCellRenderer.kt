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
package com.android.tools.adtui.ptable2

import com.intellij.ui.SimpleColoredRenderer
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

abstract class PTableCellRenderer : SimpleColoredRenderer(), TableCellRenderer {

  override fun getTableCellRendererComponent(table: JTable, value: Any,
                                             isSelected: Boolean, hasCellFocus: Boolean, row: Int, col: Int): Component {
    if (!(table is PTable && value is PTableItem)) {
      return this
    }

    // PTable shows focus for the entire row. Not per cell.
    val rowIsLead = table.selectionModel.leadSelectionIndex == row
    val hasFocus = table.hasFocus() && rowIsLead

    clear()
    border = null
    setPaintFocusBorder(hasFocus)
    font = table.font
    if (isSelected) {
      foreground = UIUtil.getTreeForeground(true, hasFocus)
      background = UIUtil.getTreeSelectionBackground(hasFocus)
    }
    else {
      foreground = table.foreground
      background = table.background
    }
    customizeCellRenderer(table, value, isSelected, hasFocus)
    return this
  }

  protected abstract fun customizeCellRenderer(table: PTable, item: PTableItem, selected: Boolean, hasFocus: Boolean)
}
