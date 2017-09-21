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

import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import javax.swing.JComponent

/**
 * Abstraction over the UI component used to display various UI elements of the [com.android.tools.idea.editors.sqlite.SqliteEditor].
 *
 * This is used by [com.android.tools.idea.sqlite.SqliteController] to avoid direct dependency on the
 * UI implementation.
 *
 * @see [SqliteViewListener] for the listener interface.
 */
interface SqliteView {
  fun addListener(listener: SqliteViewListener)
  fun removeListener(listener: SqliteViewListener)

  val component: JComponent

  fun setup()

  fun startLoading(text: String)
  fun stopLoading()

  fun displaySchema(schema: SqliteSchema)

  fun startTableLoading(table: SqliteTable)
  fun showTableColumns(columns: List<SqliteColumn>)
  fun showTableRowBatch(rows: List<SqliteRow>)
  fun stopTableLoading()

  fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable)
  fun reportErrorRelatedToTable(table: SqliteTable, message: String, t: Throwable)
}
