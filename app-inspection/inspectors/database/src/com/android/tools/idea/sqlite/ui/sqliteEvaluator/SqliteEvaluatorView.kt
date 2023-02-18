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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator

import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * Abstraction over the UI component used to evaluate user-defined SQL statements.
 *
 * This is used by [com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController] to avoid
 * direct dependency on the UI implementation.
 *
 * @see [SqliteEvaluatorView.Listener] for the listener interface.
 */
interface SqliteEvaluatorView {
  val project: Project
  /** The JComponent containing the view's UI. */
  val component: JComponent
  val tableView: TableView
  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)
  fun showSqliteStatement(sqliteStatement: String)

  fun setDatabases(databaseIds: List<SqliteDatabaseId>, selected: SqliteDatabaseId?)

  /** Notifies the view that the schema associated with [databaseId] has changed. */
  fun schemaChanged(databaseId: SqliteDatabaseId)

  /** Toggles on and off the ability to run sqlite statements */
  fun setRunSqliteStatementEnabled(enabled: Boolean)

  fun reportError(message: String, t: Throwable?)

  /** Sets a list of queries to show in the query history popup */
  fun setQueryHistory(queries: List<String>)

  /**
   * Shows a panel that shows [message] to the user. [message] will be rendered on multiple lines if
   * contains "\n"
   *
   * The panel hides the table.
   */
  fun showMessagePanel(message: String)

  /** Shows the table and hides the message panel. */
  fun showTableView()

  interface Listener {
    /** Invoked when a database is selected in the combobox */
    fun onDatabaseSelected(databaseId: SqliteDatabaseId) {}

    /** Method invoked when an sql statement needs to be evaluated. */
    fun evaluateCurrentStatement() {}

    /** Called when the sqlite statement changes */
    fun sqliteStatementTextChangedInvoked(newSqliteStatement: String) {}
  }
}
