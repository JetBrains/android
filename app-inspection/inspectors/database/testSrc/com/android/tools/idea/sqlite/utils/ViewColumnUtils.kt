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
package com.android.tools.idea.sqlite.utils

import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.tableView.ViewColumn

internal fun List<ResultSetSqliteColumn>.toViewColumns(table: SqliteTable? = null) = map {
  it.toViewColumn(table)
}

internal fun ResultSetSqliteColumn.toViewColumn(table: SqliteTable? = null): ViewColumn {
  val schemaColumn = table?.columns?.firstOrNull { it.name == name }
  return ViewColumn(
    name,
    schemaColumn?.inPrimaryKey ?: inPrimaryKey ?: false,
    schemaColumn?.isNullable ?: isNullable ?: true
  )
}
