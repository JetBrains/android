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

import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.logtab.LogTabView
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView.Listener
import javax.swing.JComponent

/**
 * Abstraction used by [com.android.tools.idea.sqlite.controllers.DatabaseInspectorController] to avoid direct dependency on the
 * UI implementation.
 *
 * @see [Listener] for the listener interface.
 */
interface DatabaseInspectorView {
  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)

  /**
   * The JComponent containing the view's UI.
   */
  val component: JComponent

  fun getLogTabView(): LogTabView

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
   * @param toAdd The list of [SqliteTable] belonging to the database schema.
   */
  fun updateDatabase(database: SqliteDatabase, toAdd: List<SqliteTable>)

  /**
   * Removes the [SqliteSchema] corresponding to the [SqliteDatabase] passed as argument.
   */
  fun removeDatabaseSchema(database: SqliteDatabase)
  fun openTab(tableId: TabId, tabName: String, component: JComponent)
  fun focusTab(tabId: TabId)
  fun closeTab(tabId: TabId)

  fun reportSyncProgress(message: String)

  fun reportError(message: String, t: Throwable)

  interface Listener {
    /** Called when the user wants to open a table */
    fun tableNodeActionInvoked(database: SqliteDatabase, table: SqliteTable)
    /** Called when the user wants to close a tab */
    fun closeTabActionInvoked(tabId: TabId)
    /** Called when the user wants to open the evaluator tab */
    fun openSqliteEvaluatorTabActionInvoked()
    /** Called when the user wants to remove a database from the list of open databases */
    fun removeDatabaseActionInvoked(database: SqliteDatabase)
    /** Called when the user wants to sync a database */
    fun reDownloadDatabaseFileActionInvoked(database: FileSqliteDatabase)
    /** Called when the user wants to refresh the schema of all open databases */
    fun refreshAllOpenDatabasesSchemaActionInvoked()
  }
}