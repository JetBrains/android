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

import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_WAVED
import java.awt.Font
import javax.swing.JTable

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
internal class StringsCellRenderer : ColoredTableCellRenderer() {
  override fun customizeCellRenderer(
    table: JTable,
    value: Any?,
    selected: Boolean,
    focusOwner: Boolean,
    viewRowIndex: Int,
    viewColumnIndex: Int
  ) {
    if (value !is String) return

    @Suppress("UNCHECKED_CAST")
    with(table as SubTable<StringResourceTableModel>) {
      customizeCellRenderer(frozenColumnTable, value, viewRowIndex, translateColumn(viewColumnIndex))
    }
  }

  private fun customizeCellRenderer(
    table: FrozenColumnTable<StringResourceTableModel>,
    value: String,
    viewRowIndex: Int,
    viewColumnIndex: Int
  ) {
    updateFontIfNecessary(table.font)

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

  private fun updateFontIfNecessary(tableFont: Font) {
    StringResourceEditor.getFont(tableFont).let { if (tableFont != it) font = it }
  }
}
