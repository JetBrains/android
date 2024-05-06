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

import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import java.util.ArrayList
import javax.swing.JComponent
import org.mockito.Mockito.mock

open class FakeSqliteEvaluatorView : SqliteEvaluatorView {
  override val project: Project = mock(Project::class.java)
  override val component: JComponent = mock(JComponent::class.java)
  override val tableView: TableView = mock(TableView::class.java)

  val listeners = ArrayList<SqliteEvaluatorView.Listener>()

  override fun addListener(listener: SqliteEvaluatorView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: SqliteEvaluatorView.Listener) {
    listeners.remove(listener)
  }

  override fun showSqliteStatement(sqliteStatement: String) {}

  override fun setDatabases(databaseIds: List<SqliteDatabaseId>, selected: SqliteDatabaseId?) {}

  override fun schemaChanged(databaseId: SqliteDatabaseId) {}

  override fun setRunSqliteStatementEnabled(enabled: Boolean) {}

  override fun reportError(message: String, t: Throwable?) {}

  override fun setQueryHistory(queries: List<String>) {}

  override fun showMessagePanel(message: String) {}

  override fun showTableView() {}
}
