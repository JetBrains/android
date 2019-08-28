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
package com.android.tools.property.ptable2.impl

import com.android.tools.property.ptable2.DefaultPTableCellRenderer
import com.android.tools.property.ptable2.PTableCellRenderer
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableVariableHeightCellEditor
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * A [TableCellRenderer] that delegates to a [PTableCellRenderer].
 *
 * A thin wrapper around a [TableCellRenderer] that can be used in a [JTable].
 * By default a [DefaultPTableCellRenderer] is used, but it can be overridden with
 * a different implementation by setting the [renderer]
 */
class PTableCellRendererWrapper: TableCellRenderer {
  var renderer: PTableCellRenderer = DefaultPTableCellRenderer()

  override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, withFocus: Boolean,
                                             row: Int, column: Int): Component? {
    // PTable shows focus for the entire row. Not per cell.
    val rowIsLead = table.selectionModel.leadSelectionIndex == row
    val hasFocus = (table.hasFocus() || table.editingRow == row) && rowIsLead
    val pTable = table as PTableImpl
    val model = pTable.model
    val item = value as PTableItem
    val isExpanded = pTable.isExpandedItem(row, column)
    val component = renderer.getEditorComponent(pTable, item, PTableColumn.fromColumn(column), model.depth(item), isSelected, hasFocus, isExpanded)
    if (pTable.isPaintingTable && component is PTableVariableHeightCellEditor && component.isCustomHeight) {
      table.setRowHeight(row, component.preferredSize.height)
    }
    return component
  }
}
