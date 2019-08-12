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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.SqliteServiceFactory
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactory
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.SqliteView
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewListener
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import java.util.TreeMap
import java.util.concurrent.Executor

/**
 * Implementation of the application logic related to viewing/editing sqlite databases.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteController(
  private val project: Project,
  private val sqliteServiceFactory: SqliteServiceFactory,
  private val viewFactory: SqliteEditorViewFactory,
  val sqliteView: SqliteView,
  edtExecutor: EdtExecutorService,
  taskExecutor: Executor
) : Disposable, SchemaProvider {
  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val taskExecutor = FutureCallbackExecutor.wrap(taskExecutor)

  /**
   * Controllers for all open tabs, keyed by id.
   *
   * <p>Multiple tables can be open at the same time in different tabs.
   * This map keeps track of corresponding controllers.
   */
  private val resultSetControllers = mutableMapOf<TabId, Disposable>()

  private val sqliteViewListener = SqliteViewListenerImpl()

  /**
   * Maps each open database to its [SqliteSchema].
   * The keys are sorted in alphabetical order on the name of the database.
   */
  private val openDatabases: TreeMap<SqliteDatabase, SqliteSchema>
    = TreeMap(Comparator.comparing { database: SqliteDatabase -> database.name })

  init {
    Disposer.register(project, this)
  }

  fun setUp() {
    sqliteView.addListener(sqliteViewListener)
  }

  fun openSqliteDatabase(sqliteFile: VirtualFile) {
    val existingDatabase = openDatabases.keys.firstOrNull { it.name == sqliteFile.path }
    if (existingDatabase != null) {
      closeDatabase(existingDatabase)
    }

    val sqliteService = sqliteServiceFactory.getSqliteService(sqliteFile, PooledThreadExecutor.INSTANCE)
    val database = SqliteDatabase(sqliteFile.path, sqliteService)
    Disposer.register(project, database)

    openDatabase(database) { openDatabase ->
      readDatabaseSchema(openDatabase) { schema -> addNewDatabaseSchema(database, schema) }
    }
  }

  private fun openDatabase(database: SqliteDatabase, onDatabaseOpened: (SqliteDatabase) -> Unit) {
    sqliteView.startLoading("Opening Sqlite database...")
    taskExecutor.addCallback(database.sqliteService.openDatabase(), object : FutureCallback<Unit> {
      override fun onSuccess(result: Unit?) {
        onDatabaseOpened(database)
      }

      override fun onFailure(t: Throwable) {
        sqliteView.reportErrorRelatedToService(database.sqliteService, "Error opening Sqlite database", t)
      }
    })
  }

  fun hasOpenDatabase() = openDatabases.isNotEmpty()

  override fun getSchema(database: SqliteDatabase) = openDatabases[database]

  fun runSqlStatement(query: String) {
    val sqliteEvaluatorController = openNewEvaluatorTab()
    // TODO this will be fixed in the next CL
    sqliteEvaluatorController.evaluateSqlStatement(openDatabases.keys.first(), query)
  }

  override fun dispose() {
    sqliteView.removeListener(sqliteViewListener)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeListeners() }
  }

  private fun readDatabaseSchema(database: SqliteDatabase, onSchemaRead: (SqliteSchema) -> Unit) {
    val futureSchema = database.sqliteService.readSchema()

    edtExecutor.addListener(futureSchema) {
      if (!Disposer.isDisposed(this@SqliteController)) {
        sqliteView.stopLoading()
      }
    }

    edtExecutor.addCallback(futureSchema, object : FutureCallback<SqliteSchema> {
      override fun onSuccess(sqliteSchema: SqliteSchema?) {
        sqliteSchema?.let { onSchemaRead(sqliteSchema) }
      }

      override fun onFailure(t: Throwable) {
        if (!Disposer.isDisposed(this@SqliteController)) {
          sqliteView.reportErrorRelatedToService(database.sqliteService, "Error reading Sqlite database", t)
        }
      }
    })
  }

  private fun addNewDatabaseSchema(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    if (Disposer.isDisposed(this)) return
    val index = openDatabases.headMap(database).size
    sqliteView.addDatabaseSchema(database, sqliteSchema, index)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.addDatabase(database, index) }
    openDatabases[database] = sqliteSchema
  }

  private fun closeDatabase(existingDatabase: SqliteDatabase) {
    val tabsToClose = resultSetControllers.keys
      .filterIsInstance<TabId.TableTab>()
      .filter { it.database == existingDatabase }

    tabsToClose.forEach { closeTab(it) }

    val index = openDatabases.headMap(existingDatabase).size
    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeDatabase(index) }

    sqliteView.removeDatabaseSchema(existingDatabase)

    openDatabases.remove(existingDatabase)
    Disposer.dispose(existingDatabase)
  }

  private fun closeTab(tabId: TabId) {
    sqliteView.closeTab(tabId)
    val controller = resultSetControllers.remove(tabId)
    controller?.let(Disposer::dispose)
  }

  private fun updateDatabaseSchema(database: SqliteDatabase) {
    val oldSchema = openDatabases[database] ?: return
    readDatabaseSchema(database) { newSchema ->
      updateExistingDatabaseSchemaView(database, oldSchema, newSchema)
      openDatabases[database] = newSchema
    }
  }

  private fun updateExistingDatabaseSchemaView(database: SqliteDatabase, oldSchema: SqliteSchema, newSqliteSchema: SqliteSchema) {
    val toRemove = oldSchema.tables.filter { !newSqliteSchema.tables.contains(it) }
    val toAdd = newSqliteSchema.tables
      .sortedBy { it.name }
      .mapIndexed { index, table -> IndexedSqliteTable(index, table) }
      .filter { !oldSchema.tables.contains(it.sqliteTable) }
    sqliteView.updateDatabase(database, toRemove, toAdd)
  }

  private fun openNewEvaluatorTab(): SqliteEvaluatorController {
    val tabId = TabId.AdHocQueryTab()

    val sqliteEvaluatorView = viewFactory.createEvaluatorView(project, this)
    // TODO(b/136556640) What name should we use for these tabs?
    sqliteView.displayResultSet(tabId, "New Query", sqliteEvaluatorView.component)

    val sqliteEvaluatorController = SqliteEvaluatorController(
      this@SqliteController,
      sqliteEvaluatorView, edtExecutor
    ).also { it.setUp() }
    sqliteEvaluatorController.addListener(SqliteEvaluatorControllerListenerImpl())

    resultSetControllers[tabId] = sqliteEvaluatorController

    openDatabases.keys.forEachIndexed { index, sqliteDatabase ->
      sqliteEvaluatorController.addDatabase(sqliteDatabase, index)
    }

    return sqliteEvaluatorController
  }

  private inner class SqliteViewListenerImpl : SqliteViewListener {
    override fun tableNodeActionInvoked(database: SqliteDatabase, table: SqliteTable) {
      val tableId = TabId.TableTab(database, table.name)
      if (tableId in resultSetControllers) {
        sqliteView.focusTab(tableId)
        return
      }

      val sqliteService = database.sqliteService

      edtExecutor.addCallback(sqliteService.readTable(table), object : FutureCallback<SqliteResultSet> {
        override fun onSuccess(sqliteResultSet: SqliteResultSet?) {
          if (sqliteResultSet != null) {

            val tableView = viewFactory.createTableView()
            sqliteView.displayResultSet(tableId, table.name, tableView.component)

            val resultSetController = ResultSetController(
              this@SqliteController,
              tableView, table.name, sqliteResultSet,
              edtExecutor
            ).also { it.setUp() }

            resultSetControllers[tableId] = resultSetController
          }
        }

        override fun onFailure(t: Throwable) {
          sqliteView.reportErrorRelatedToService(sqliteService, "Error reading Sqlite table \"${table.name}\"", t)
        }
      })
    }

    override fun openSqliteEvaluatorTabActionInvoked() {
      openNewEvaluatorTab()
    }

    override fun closeTabActionInvoked(tabId: TabId) {
      closeTab(tabId)
    }

    override fun removeDatabaseActionInvoked(database: SqliteDatabase) {
      closeDatabase(database)
    }
  }

  private inner class SqliteEvaluatorControllerListenerImpl : SqliteEvaluatorControllerListener {
    override fun onSchemaUpdated(database: SqliteDatabase) {
      updateDatabaseSchema(database)
    }
  }
}

sealed class TabId {
  data class TableTab(val database: SqliteDatabase, val tableName: String) : TabId()
  class AdHocQueryTab : TabId()
}