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
package com.android.tools.idea.sqlite.ui

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow

/**
 * Interface used to abstract views that display the content of SQL tables.
 */
interface ResultSetView {
  fun startTableLoading(tableName: String?)
  fun showTableColumns(columns: List<SqliteColumn>)
  fun showTableRowBatch(rows: List<SqliteRow>)
  fun stopTableLoading()
  fun reportErrorRelatedToTable(tableName: String?, message: String, t: Throwable)
}