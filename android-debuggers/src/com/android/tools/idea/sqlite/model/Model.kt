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
package com.android.tools.idea.sqlite.model

import com.android.tools.idea.sqlite.SqliteService
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import java.sql.JDBCType

/**
 * Representation of a database instance.
 */
data class SqliteDatabase(
  val virtualFile: VirtualFile,
  val sqliteService: SqliteService
) : Disposable {

  // TODO(b/139525976)
  val name = virtualFile.path.split("data/data/").getOrNull(1)?.replace("databases/", "")
             ?: virtualFile.path

  override fun dispose() {
    sqliteService.closeDatabase().get()
  }
}

/** Representation of the Sqlite database schema */
data class SqliteSchema(val tables: List<SqliteTable>)

/** Representation of the Sqlite database table
 *
 * @see [https://www.sqlite.org/lang_createview.html] for isView
 **/
data class SqliteTable(val name: String, val columns: List<SqliteColumn>, val isView: Boolean)

/** Representation of the Sqlite table row */
data class SqliteRow(val values: List<SqliteColumnValue>)

/** Representation of a Sqlite table column value */
data class SqliteColumnValue(val column: SqliteColumn, val value: Any?)

/** Representation of a Sqlite table column */
data class SqliteColumn(val name: String, val type: JDBCType)