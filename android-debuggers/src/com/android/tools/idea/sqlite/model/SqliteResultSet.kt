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
package com.android.tools.idea.sqlite.model

import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable

/**
 * Similar to JDBC result set, but simplified to match the abstraction required
 * by the SQLite viewer.
 *
 * All operations, except [dispose], are asynchronous, where completion is communicated through
 * [ListenableFuture] return values.
 *
 * The [dispose] method cancels all pending operations and releases all resources associated with
 * the result set.
 */
interface SqliteResultSet : Disposable {
  var rowBatchSize: Int
  fun columns() : ListenableFuture<List<SqliteColumn>>
  fun nextRowBatch(): ListenableFuture<List<SqliteRow>>
}