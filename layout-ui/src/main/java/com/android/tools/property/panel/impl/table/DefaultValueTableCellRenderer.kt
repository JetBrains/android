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

import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellRenderer
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableItem
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

class DefaultValueTableCellRenderer : SimpleColoredComponent(), PTableCellRenderer {

  override fun getEditorComponent(
    table: PTable,
    item: PTableItem,
    column: PTableColumn,
    depth: Int,
    isSelected: Boolean,
    hasFocus: Boolean,
    isExpanded: Boolean,
  ): JComponent? {
    clear()
    setPaintFocusBorder(hasFocus)
    font = table.activeFont
    append(item.value.orEmpty())
    if (isSelected && hasFocus) {
      foreground = UIUtil.getTableForeground(true, true)
      background = UIUtil.getTableBackground(true, true)
    } else {
      foreground = table.foregroundColor
      background = table.backgroundColor
    }
    border = JBUI.Borders.customLine(table.gridLineColor, 0, 1, 0, 0)
    return this
  }
}
