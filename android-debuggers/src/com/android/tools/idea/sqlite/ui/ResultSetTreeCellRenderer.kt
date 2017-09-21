/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.JdkConstants
import java.sql.JDBCType
import javax.swing.JTable
import javax.swing.SwingConstants

private const val TEXT_RENDERER_HORIZ_PADDING = 6

@JdkConstants.HorizontalAlignment
fun headerAlignment(column: SqliteColumn): Int {
  return when (column.type) {
    JDBCType.BIT,
    JDBCType.TINYINT,
    JDBCType.SMALLINT,
    JDBCType.INTEGER,
    JDBCType.BIGINT,
    JDBCType.FLOAT,
    JDBCType.REAL,
    JDBCType.DOUBLE,
    JDBCType.NUMERIC,
    JDBCType.BOOLEAN,
    JDBCType.ROWID,
    JDBCType.DECIMAL -> SwingConstants.TRAILING
    else -> SwingConstants.LEADING
  }
}

/**
 * Implementation of [ColoredTableCellRenderer] for header cells of the [JTable] used to display values of a
 * [com.android.tools.idea.sqlite.model.SqliteResultSet].
 */
class ResultSetTreeCellRenderer : ColoredTableCellRenderer() {

  override fun customizeCellRenderer(table: JTable?, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value is SqliteColumnValue) {
      when (value.column.type) {
        JDBCType.NULL -> appendNullValueToCell()

        JDBCType.BIT,
        JDBCType.TINYINT,
        JDBCType.SMALLINT,
        JDBCType.INTEGER,
        JDBCType.BIGINT,
        JDBCType.FLOAT,
        JDBCType.DOUBLE,
        JDBCType.CHAR,
        JDBCType.VARCHAR,
        JDBCType.LONGVARCHAR,
        JDBCType.BOOLEAN,
        JDBCType.NCHAR,
        JDBCType.NVARCHAR,
        JDBCType.REAL,
        JDBCType.NUMERIC,
        JDBCType.DECIMAL,
        JDBCType.DATE,
        JDBCType.TIME,
        JDBCType.TIMESTAMP,
        JDBCType.TIME_WITH_TIMEZONE,
        JDBCType.TIMESTAMP_WITH_TIMEZONE,
        JDBCType.LONGNVARCHAR -> appendStringValueToCell(value)

        JDBCType.BINARY,
        JDBCType.VARBINARY,
        JDBCType.LONGVARBINARY,
        JDBCType.OTHER,
        JDBCType.JAVA_OBJECT,
        JDBCType.DISTINCT,
        JDBCType.STRUCT,
        JDBCType.ARRAY,
        JDBCType.BLOB,
        JDBCType.CLOB,
        JDBCType.REF,
        JDBCType.DATALINK,
        JDBCType.ROWID,
        JDBCType.NCLOB,
        JDBCType.SQLXML,
        JDBCType.REF_CURSOR -> appendUnsupportedDataTypeToCell()
      }
    }
    else {
      appendUnsupportedDataTypeToCell()
    }

    border = JBUI.Borders.empty(0, TEXT_RENDERER_HORIZ_PADDING / 2)
  }

  /**
   * Appends the value of the [SqliteColumnValue] element to the current cell.
   */
  private fun appendStringValueToCell(columnValue: SqliteColumnValue) {
    columnValue.value?.let {
      append(it.toString())
      setTextAlign(headerAlignment(columnValue.column))
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