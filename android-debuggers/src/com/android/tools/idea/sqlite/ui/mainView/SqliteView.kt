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
package com.android.tools.idea.sqlite.ui.mainView

import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import javax.swing.JComponent

/**
 * Abstraction used by [com.android.tools.idea.sqlite.controllers.SqliteController] to avoid direct dependency on the
 * UI implementation.
 *
 * @see [SqliteViewListener] for the listener interface.
 */
interface SqliteView {
  fun addListener(listener: SqliteViewListener)
  fun removeListener(listener: SqliteViewListener)

  /**
   * The JComponent containing the view's UI.
   */
  val component: JComponent

  fun startLoading(text: String)
  fun stopLoading()

  /**
   * Adds a new [SqliteSchema] at a specific position among other schemas.
   * @param database The database containing the schema.
   * @param schema The schema to add.
   * @param index The index at which the schema should be added.
   */
  fun addDatabaseSchema(database: SqliteDatabase, schema: SqliteSchema, index: Int)

  /**
   * Updates the UI for an existing database, by adding and removing tables from its schema.
   * @param database The database that needs to be updated.
   * @param toRemove The list of [SqliteTable] to remove from the database schema.
   * @param toAdd The list of [IndexedSqliteTable] to add to the database schema. Each table is added at the specified index.
   */
  fun updateDatabase(database: SqliteDatabase, toRemove: List<SqliteTable>, toAdd: List<IndexedSqliteTable>)

  /**
   * Removes the [SqliteSchema] corresponding to the [SqliteDatabase] passed as argument.
   */
  fun removeDatabaseSchema(database: SqliteDatabase)
  fun displayResultSet(tableId: TabId, tableName: String, component: JComponent)
  fun focusTab(tabId: TabId)
  fun closeTab(tabId: TabId)

  fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable)
}

interface SqliteViewListener {
  /** Called when the user wants to open a table */
  fun tableNodeActionInvoked(database: SqliteDatabase, table: SqliteTable)
  /** Called when the user wants to close a tab */
  fun closeTabActionInvoked(tabId: TabId)
  /** Called when the user wants to open the evaluator tab */
  fun openSqliteEvaluatorTabActionInvoked()
  /** Called when the user wants to remove a database from the list of open databases */
  fun removeDatabaseActionInvoked(database: SqliteDatabase)
}

/**
 * Class containing a [SqliteTable] and its index among other tables in the UI.
 */
data class IndexedSqliteTable(val index: Int, val sqliteTable: SqliteTable)