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

import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.transform
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable

/**
 * Interface to access the result of a SQLite query.
 *
 * All operations, except [dispose], are asynchronous, where completion is communicated through
 * [ListenableFuture] return values.
 *
 * The [dispose] method cancels all pending operations and releases all resources associated with
 * the result set.
 */
interface SqliteResultSet : Disposable {
  fun SqliteStatement.toRowCountStatement() =
    this.transform(SqliteStatementType.SELECT) { "SELECT COUNT(*) FROM ($it)" }
  fun SqliteStatement.toSelectLimitOffset(rowOffset: Int, rowBatchSize: Int) =
    this.transform(SqliteStatementType.SELECT) {
      "SELECT * FROM ($it) LIMIT $rowOffset, $rowBatchSize"
    }

  val columns: ListenableFuture<List<ResultSetSqliteColumn>>

  /**
   * Returns the total amount of rows available to this result set. This number is obtained by
   * running a `SELECT COUNT(*) FROM (sqliteStatement)`, sqliteStatement can be anything.
   */
  val totalRowCount: ListenableFuture<Int>

  /**
   * Returns a list of [SqliteRow]s.
   * @param rowOffset The row from which the returned list of rows should start. Must be >= 0
   * @param rowBatchSize The maximum amount of rows returned. Must be > 0
   */
  fun getRowBatch(rowOffset: Int, rowBatchSize: Int): ListenableFuture<List<SqliteRow>>
}

/** Checks that [rowOffset] is >= 0 and [rowBatchSize] is > 0. */
internal fun checkOffsetAndSize(rowOffset: Int, rowBatchSize: Int) {
  require(rowOffset >= 0) { "Offset must be >= 0." }
  require(rowBatchSize > 0) { "Row batch size must be > 0." }
}
