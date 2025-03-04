/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table

import com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_WAVED
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

private val CELL_ERROR_ATTRIBUTES = SimpleTextAttributes(STYLE_WAVED, JBColor.red)

/**
 * Chops the string from the first instance of [delimiter] and replaces that with `"[...]"`
 *
 * Note this is different from [String.replaceAfter] because that would leave the [delimiter] in place.
 */
private fun String.clip(delimiter: Char): String =
  if (contains(delimiter)) "${substringBefore(delimiter)}[...]" else this

/** Translates the column index from sub-table indexing to overall table indexing. */
private fun SubTable<StringResourceTableModel>.translateColumn(column: Int): Int {
  // If we are our parent's frozen table, the column indices are the same, otherwise we must offset
  // by the number of columns in the parent's frozen table.
  return if (this === frozenColumnTable.frozenTable) column else column + frozenColumnTable.frozenColumnCount
}

/** Controls rendering of cells in the [StringResourceTable] displaying [String] values. */
internal class StringsCellRenderer : SimpleColoredRenderer(), TableCellRenderer {
  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocusDontUse: Boolean,
    row: Int,
    column: Int
  ): Component {
    clear()
    @Suppress("UNCHECKED_CAST")
    val subTable = table as SubTable<StringResourceTableModel>
    val hasFocus = subTable.frozenColumnTable.frozenTable.hasFocus() || subTable.frozenColumnTable.scrollableTable.hasFocus()
    foreground = UIUtil.getTableForeground(isSelected, hasFocus)
    background = UIUtil.getTableBackground(isSelected, hasFocus)
    font = table.font
    customizeCellRenderer(subTable.frozenColumnTable, value as? String ?: "", row, subTable.translateColumn(column))
    return this
  }

  private fun customizeCellRenderer(
    table: FrozenColumnTable<StringResourceTableModel>,
    value: String,
    viewRowIndex: Int,
    viewColumnIndex: Int
  ) {
    val modelRowIndex = table.convertRowIndexToModel(viewRowIndex)
    val modelColumnIndex = table.convertColumnIndexToModel(viewColumnIndex)
    val problem = table.model.getCellProblem(modelRowIndex, modelColumnIndex).also { toolTipText = it }

    val attributes = when {
      problem == null -> REGULAR_ATTRIBUTES
      modelColumnIndex == KEY_COLUMN -> ERROR_ATTRIBUTES
      else -> CELL_ERROR_ATTRIBUTES
    }

    append(value.clip('\n'), attributes)
  }
}
