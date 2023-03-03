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
package com.android.tools.idea.sqlite.databaseConnection

import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable

/**
 * Abstraction over operations allowed on a single underlying sqlite database.
 *
 * All operations are asynchronous, where completion is communicated through [ListenableFuture]
 * return values.
 */
interface DatabaseConnection : Disposable {
  fun close(): ListenableFuture<Unit>
  fun readSchema(): ListenableFuture<SqliteSchema>

  /**
   * Use this method for Query statements (like SELECT or EXPLAIN) that return a list of rows.
   *
   * @return a [SqliteResultSet] for [sqliteStatement] that can be used to navigate the result of
   *   the query.
   */
  fun query(sqliteStatement: SqliteStatement): ListenableFuture<SqliteResultSet>

  /**
   * Executes [sqliteStatement] and ignore the result. Use this method to run SQLite statements that
   * have no result, such as UPDATE and INSERT
   */
  fun execute(sqliteStatement: SqliteStatement): ListenableFuture<Unit>

  override fun dispose() {
    close()
  }
}
