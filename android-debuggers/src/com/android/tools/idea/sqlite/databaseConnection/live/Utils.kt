/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.getRowIdName

/**
 * Builds a [SqliteInspectorProtocol.Command] from a [SqliteStatement] and a database connection id.
 */
internal fun buildQueryCommand(sqliteStatement: SqliteStatement, databaseConnectionId: Int): SqliteInspectorProtocol.Command {
  // TODO(blocked b/144336989) pass SqliteStatement object instead of String.
  val queryBuilder = SqliteInspectorProtocol.QueryCommand.newBuilder()
    .setQuery(sqliteStatement.assignValuesToParameters()).setDatabaseId(databaseConnectionId)

  return SqliteInspectorProtocol.Command.newBuilder().setQuery(queryBuilder).build()
}

internal fun SqliteInspectorProtocol.CellValue.toSqliteColumnValue(columnName: String): SqliteColumnValue {
  return when (valueCase) {
    SqliteInspectorProtocol.CellValue.ValueCase.STRING_VALUE ->
      SqliteColumnValue(columnName, stringValue)
    SqliteInspectorProtocol.CellValue.ValueCase.FLOAT_VALUE ->
      SqliteColumnValue(columnName, floatValue)
    SqliteInspectorProtocol.CellValue.ValueCase.BLOB_VALUE ->
      SqliteColumnValue(columnName, blobValue)
    SqliteInspectorProtocol.CellValue.ValueCase.INT_VALUE ->
      SqliteColumnValue(columnName, intValue)
    else -> SqliteColumnValue(columnName, "null")
  }
}

internal fun List<SqliteInspectorProtocol.Table>.toSqliteSchema(): SqliteSchema {
  val tables = map { table ->
    val columns = table.columnsList.map { it.toSqliteColumn() }
    val rowIdName = getRowIdName(columns)
    // TODO(blocked): set isView
    SqliteTable(table.name, columns, rowIdName, false)
  }
  return SqliteSchema(tables)
}

private fun SqliteInspectorProtocol.Column.toSqliteColumn(): SqliteColumn {
  return SqliteColumn(name, SqliteAffinity.fromTypename(type), !isNotNull, primaryKey > 0)
}