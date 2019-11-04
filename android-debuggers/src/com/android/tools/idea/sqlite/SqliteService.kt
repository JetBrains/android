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
package com.android.tools.idea.sqlite

import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.google.common.util.concurrent.ListenableFuture

/**
 * Abstraction over operations allowed on a single underlying sqlite database.
 *
 * All operations are asynchronous, where completion is communicated through [ListenableFuture] return values.
 */
interface SqliteService {
  fun closeDatabase(): ListenableFuture<Unit>
  fun readSchema(): ListenableFuture<SqliteSchema>

  /**
   * Executes a query on the database.
   *
   * @see java.sql.PreparedStatement.executeQuery
   */
  fun executeQuery(sqLiteStatement: SqliteStatement): ListenableFuture<SqliteResultSet>

  /**
   * Executes an update on the database.
   *
   * @see java.sql.PreparedStatement.executeUpdate
   */
  fun executeUpdate(sqLiteStatement: SqliteStatement): ListenableFuture<Int>
}
