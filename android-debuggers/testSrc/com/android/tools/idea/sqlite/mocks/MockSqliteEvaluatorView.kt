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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewListener
import java.util.ArrayList

open class MockSqliteEvaluatorView : SqliteEvaluatorView {

  val listeners = ArrayList<SqliteEvaluatorViewListener>()

  override fun show() { }

  override fun resetView() { }

  override fun requestFocus() { }

  override fun addListener(listener: SqliteEvaluatorViewListener) { listeners.add(listener) }

  override fun removeListener(listener: SqliteEvaluatorViewListener) { listeners.remove(listener) }

  override fun startTableLoading(tableName: String?) { }

  override fun showTableColumns(columns: List<SqliteColumn>) { }

  override fun showTableRowBatch(rows: List<SqliteRow>) { }

  override fun stopTableLoading() { }

  override fun reportErrorRelatedToTable(tableName: String?, message: String, t: Throwable) { }
}