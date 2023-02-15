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
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView.Listener
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Abstraction used by [com.android.tools.idea.sqlite.controllers.DatabaseInspectorController] to
 * avoid direct dependency on the UI implementation.
 *
 * @see [Listener] for the listener interface.
 */
interface DatabaseInspectorView {
  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)

  /** The JComponent containing the view's UI. */
  val component: JComponent

  /** Updates the UI by applying [DatabaseDiffOperation]s. */
  fun updateDatabases(databaseDiffOperations: List<DatabaseDiffOperation>)

  /**
   * Updates the UI for an existing database, by adding and removing tables from its schema and
   * columns from its tables.
   * @param viewDatabase The database that needs to be updated.
   * @param diffOperations List of operations to perform the diff of [viewDatabase]'s schema in the
   * view.
   */
  fun updateDatabaseSchema(viewDatabase: ViewDatabase, diffOperations: List<SchemaDiffOperation>)

  fun openTab(tabId: TabId, tabName: String, tabIcon: Icon, component: JComponent)
  fun focusTab(tabId: TabId)
  fun closeTab(tabId: TabId)

  fun updateKeepConnectionOpenButton(keepOpen: Boolean)

  fun reportSyncProgress(message: String)

  fun reportError(message: String, throwable: Throwable?)

  /** If [state] is false, it prevents the refresh button from ever becoming enabled */
  fun setRefreshButtonState(state: Boolean)

  /**
   * Shows a panel in the right side of the view that serves as loading indicator for offline mode
   */
  fun showEnterOfflineModePanel(filesDownloaded: Int, totalFilesToDownload: Int)

  /**
   * Shows a panel containing a message and a link to learn more about why offline might be
   * unavailable
   */
  fun showOfflineModeUnavailablePanel()

  interface Listener {
    /** Called when the user wants to open a table */
    fun tableNodeActionInvoked(databaseId: SqliteDatabaseId, table: SqliteTable)
    /** Called when the user wants to close a tab */
    fun closeTabActionInvoked(tabId: TabId)
    /** Called when the user wants to open the evaluator tab */
    fun openSqliteEvaluatorTabActionInvoked()
    /** Called when the user wants to refresh the schema of all open databases */
    fun refreshAllOpenDatabasesSchemaActionInvoked()
    /** Called to request the on-device inspector to force database connections to remain open */
    fun toggleKeepConnectionOpenActionInvoked()
    /** Called when the user wants to stop entering offline mode */
    fun cancelOfflineModeInvoked()
    /** Called when user wants to export data */
    fun showExportToFileDialogInvoked(exportDialogParams: ExportDialogParams)
  }
}

/** Class containing a [SqliteTable] and its index among other tables in the schema. */
data class IndexedSqliteTable(val sqliteTable: SqliteTable, val index: Int)

/** Class containing a [SqliteColumn] and its index among other columns in the table. */
data class IndexedSqliteColumn(val sqliteColumn: SqliteColumn, val index: Int)

/**
 * Subclasses of this class represent operations to do in the UI in order to perform the diff of a
 * database's schema
 */
sealed class SchemaDiffOperation

data class AddTable(
  val indexedSqliteTable: IndexedSqliteTable,
  val columns: List<IndexedSqliteColumn>
) : SchemaDiffOperation()

data class RemoveTable(val tableName: String) : SchemaDiffOperation()

data class AddColumns(
  val tableName: String,
  val columns: List<IndexedSqliteColumn>,
  val newTable: SqliteTable
) : SchemaDiffOperation()

data class RemoveColumns(
  val tableName: String,
  val columnsToRemove: List<SqliteColumn>,
  val newTable: SqliteTable
) : SchemaDiffOperation()

/**
 * Subclasses of this class represent operations to do in the UI in order to perform the diff of
 * visible databases
 */
sealed class DatabaseDiffOperation {
  data class AddDatabase(
    val viewDatabase: ViewDatabase,
    val schema: SqliteSchema?,
    val index: Int
  ) : DatabaseDiffOperation()
  data class RemoveDatabase(val viewDatabase: ViewDatabase) : DatabaseDiffOperation()
}

data class ViewDatabase(val databaseId: SqliteDatabaseId, val isOpen: Boolean)
