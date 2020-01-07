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
package com.android.tools.idea.sqlite.ui.renderers

import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import javax.swing.JTable

/**
 * Implementation of [ColoredTableCellRenderer] for cells of a [JTable] used to display [SqliteColumnValue]s.
 */
class SqliteColumnValueTableCellRenderer : ColoredTableCellRenderer() {
  private val TEXT_RENDERER_HORIZ_PADDING = 6

  override fun customizeCellRenderer(table: JTable?, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value !is SqliteColumnValue) {
      appendUnsupportedDataTypeToCell()
    } else {
      appendStringValueToCell(value)
    }

    border = JBUI.Borders.empty(0, TEXT_RENDERER_HORIZ_PADDING / 2)
  }

  /**
   * Appends the value of the [SqliteColumnValue] element to the current cell.
   */
  private fun appendStringValueToCell(columnValue: SqliteColumnValue) {
    columnValue.value?.let {
      append(it.toString())
    } ?: appendNullValueToCell()
  }

  /**
   * Appends the String "NULL" to the current cell.
   */
  private fun appendNullValueToCell() {
    append("NULL", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
  }

  /**
   * Appends the String "Unsupported data type" to the current cell.
   */
  private fun appendUnsupportedDataTypeToCell() {
    append("Unsupported data type", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
  }
}