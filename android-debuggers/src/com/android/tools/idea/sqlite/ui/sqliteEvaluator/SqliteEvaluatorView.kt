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

import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * Abstraction over the UI component used to evaluate user-defined SQL statements.
 *
 * This is used by [com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController] to avoid direct dependency on the
 * UI implementation.
 *
 * @see [SqliteEvaluatorViewListener] for the listener interface.
 */
interface SqliteEvaluatorView {
  val project: Project
  /**
   * The JComponent containing the view's UI.
   */
  val component: JComponent
  val tableView: TableView
  fun addListener(listener: SqliteEvaluatorViewListener)
  fun removeListener(listener: SqliteEvaluatorViewListener)
  fun showSqliteStatement(sqliteStatement: String)
  /**
   * Adds a new [SqliteDatabase] at a specific position among other databases.
   * @param database The database to add.
   * @param databaseName The name to use in the UI for this database.
   * @param index The index at which the database should be added.
   */
  fun addDatabase(database: SqliteDatabase, databaseName: String, index: Int)
  fun removeDatabase(index: Int)
}

interface SqliteEvaluatorViewListener {
  /**
   * Method invoked when an sql statement needs to be evaluated.
   */
  fun evaluateSqlActionInvoked(database: SqliteDatabase, sqliteStatement: String)
}