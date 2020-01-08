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
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.text.trimMiddle
import java.util.TreeMap
import java.util.concurrent.Executor
import javax.swing.JComponent

/**
 * Implementation of the application logic related to viewing/editing sqlite databases.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class DatabaseInspectorControllerImpl(
  private val project: Project,
  private val model: DatabaseInspectorController.Model,
  private val viewFactory: DatabaseInspectorViewsFactory,
  edtExecutor: Executor,
  taskExecutor: Executor
) : DatabaseInspectorController {
  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val taskExecutor = FutureCallbackExecutor.wrap(taskExecutor)

  private val view = viewFactory.createDatabaseInspectorView(project)

  /**
   * Controllers for all open tabs, keyed by id.
   *
   * <p>Multiple tables can be open at the same time in different tabs.
   * This map keeps track of corresponding controllers.
   */
  private val resultSetControllers = mutableMapOf<TabId, DatabaseInspectorController.TabController>()

  private val sqliteViewListener = SqliteViewListenerImpl()

  override val component: JComponent
    get() = view.component

  override fun setUp() {
    view.addListener(sqliteViewListener)
  }

  override fun addSqliteDatabase(sqliteDatabaseFuture: ListenableFuture<SqliteDatabase>): ListenableFuture<Unit> {
    val settableFuture = SettableFuture.create<Unit>()

    view.startLoading("Getting database...")
    taskExecutor.addCallback(sqliteDatabaseFuture, object : FutureCallback<SqliteDatabase> {
      override fun onSuccess(sqliteDatabase: SqliteDatabase?) {
        if (Disposer.isDisposed(this@DatabaseInspectorControllerImpl)) return

        Disposer.register(this@DatabaseInspectorControllerImpl, sqliteDatabase!!.databaseConnection)
        readDatabaseSchema(sqliteDatabase) { schema -> addNewDatabase(sqliteDatabase, schema) }
        settableFuture.set(Unit)
      }

      override fun onFailure(t: Throwable) {
        view.reportError("Error getting database", t)
        settableFuture.setException(t)
      }
    })

    return settableFuture
  }

  override fun runSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    val sqliteEvaluatorController = openNewEvaluatorTab()
    return sqliteEvaluatorController.evaluateSqlStatement(database, sqliteStatement)
  }

  override fun closeDatabase(database: SqliteDatabase): ListenableFuture<Unit> {
    // TODO(b/143873070) when a database is closed with the close button the corresponding file is not deleted.
    if (!model.openDatabases.containsKey(database)) {
      return Futures.immediateFuture(Unit)
    }

    val tabsToClose = resultSetControllers.keys
      .filterIsInstance<TabId.TableTab>()
      .filter { it.database == database }

    tabsToClose.forEach { closeTab(it) }

    val index = model.getSortedIndexOf(database)
    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeDatabase(index) }

    view.removeDatabaseSchema(database)

    model.remove(database)

    return taskExecutor.executeAsync {
      Disposer.dispose(database.databaseConnection)
    }
  }

  override fun dispose() {
    view.removeListener(sqliteViewListener)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeListeners() }
  }

  private fun readDatabaseSchema(database: SqliteDatabase, onSchemaRead: (SqliteSchema) -> Unit) {
    val futureSchema = database.databaseConnection.readSchema()

    edtExecutor.addListener(futureSchema) {
      if (!Disposer.isDisposed(this@DatabaseInspectorControllerImpl)) {
        view.stopLoading()
      }
    }

    edtExecutor.addCallback(futureSchema, object : FutureCallback<SqliteSchema> {
      override fun onSuccess(sqliteSchema: SqliteSchema?) {
        sqliteSchema?.let { onSchemaRead(sqliteSchema) }
      }

      override fun onFailure(t: Throwable) {
        if (!Disposer.isDisposed(this@DatabaseInspectorControllerImpl)) {
          view.reportError("Error reading Sqlite database", t)
        }
      }
    })
  }

  private fun addNewDatabase(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    if (Disposer.isDisposed(this)) return
    val index = model.getSortedIndexOf(database)
    view.addDatabaseSchema(database, sqliteSchema, index)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.addDatabase(database, index) }

    model.add(database, sqliteSchema)
  }

  private fun closeTab(tabId: TabId) {
    view.closeTab(tabId)
    val controller = resultSetControllers.remove(tabId)
    controller?.let(Disposer::dispose)
  }

  private fun updateDatabaseSchema(database: SqliteDatabase) {
    readDatabaseSchema(database) { newSchema ->
      updateExistingDatabaseSchemaView(database, newSchema)
      model.add(database, newSchema)
    }
  }

  private fun updateExistingDatabaseSchemaView(database: SqliteDatabase, newSqliteSchema: SqliteSchema) {
    val toAdd = newSqliteSchema.tables.sortedBy { it.name }
    view.updateDatabase(database, toAdd)
  }

  private fun openNewEvaluatorTab(): SqliteEvaluatorController {
    val tabId = TabId.AdHocQueryTab()

    val sqliteEvaluatorView = viewFactory.createEvaluatorView(
      project,
      object : SchemaProvider { override fun getSchema(database: SqliteDatabase) = model.openDatabases[database] },
      viewFactory.createTableView()
    )

    // TODO(b/136556640) What name should we use for these tabs?
    view.openTab(tabId, "New Query", sqliteEvaluatorView.component)

    val sqliteEvaluatorController = SqliteEvaluatorController(
      project,
      sqliteEvaluatorView,
      viewFactory,
      edtExecutor
    )
    Disposer.register(project, sqliteEvaluatorController)
    sqliteEvaluatorController.setUp()

    sqliteEvaluatorController.addListener(SqliteEvaluatorControllerListenerImpl())

    resultSetControllers[tabId] = sqliteEvaluatorController

    model.openDatabases.keys.forEachIndexed { index, sqliteDatabase ->
      sqliteEvaluatorController.addDatabase(sqliteDatabase, index)
    }

    return sqliteEvaluatorController
  }

  private inner class SqliteViewListenerImpl : DatabaseInspectorView.Listener {
    override fun tableNodeActionInvoked(database: SqliteDatabase, table: SqliteTable) {
      val tableId = TabId.TableTab(database, table.name)
      if (tableId in resultSetControllers) {
        view.focusTab(tableId)
        return
      }

      val databaseConnection = database.databaseConnection

      val tableView = viewFactory.createTableView()
      view.openTab(tableId, table.name, tableView.component)

      val tableController = TableController(
        view = tableView,
        table = table,
        databaseConnection = databaseConnection,
        sqliteStatement = SqliteStatement(selectAllAndRowIdFromTable(table)),
        edtExecutor = edtExecutor
      )
      Disposer.register(project, tableController)

      edtExecutor.addCallback(tableController.setUp(), object : FutureCallback<Unit> {
        override fun onSuccess(result: Unit?) {
          resultSetControllers[tableId] = tableController
        }

        override fun onFailure(t: Throwable) {
          view.reportError("Error reading Sqlite table \"${table.name}\"", t)
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

    override fun reDownloadDatabaseFileActionInvoked(database: FileSqliteDatabase) {
      val downloadFuture = DatabaseInspectorProjectService.getInstance(project).reDownloadAndOpenFile(database, object : DownloadProgress {
        override val isCancelled: Boolean
          get() = false

        override fun onStarting(entryFullPath: String) {
          view.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: start sync")
        }

        override fun onProgress(entryFullPath: String, currentBytes: Long, totalBytes: Long) {
          view.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: sync progress $currentBytes/$totalBytes")
        }

        override fun onCompleted(entryFullPath: String) {
          view.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: sync completed")
        }
      })

      edtExecutor.transform(downloadFuture) {
        view.reportSyncProgress("")
      }
    }
  }

  private inner class SqliteEvaluatorControllerListenerImpl : SqliteEvaluatorController.Listener {
    override fun onSchemaUpdated(database: SqliteDatabase) {
      updateDatabaseSchema(database)
    }
  }
}

/**
 * Interface that defines the contract of a SqliteController.
 */
interface DatabaseInspectorController : Disposable {
  val component: JComponent

  fun setUp()
  fun addSqliteDatabase(sqliteDatabaseFuture: ListenableFuture<SqliteDatabase>): ListenableFuture<Unit>
  fun runSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit>
  fun closeDatabase(database: SqliteDatabase): ListenableFuture<Unit>

  /**
   * Model for Database Inspector. Used to store and access currently open [SqliteDatabase]s and their [SqliteSchema]s.
   */
  interface Model {
    /**
     * A set of open databases sorted in alphabetical order by the name of the database.
     */
    val openDatabases: TreeMap<SqliteDatabase, SqliteSchema>

    fun getSortedIndexOf(database: SqliteDatabase): Int
    fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema)
    fun remove(database: SqliteDatabase)

    fun addListener(modelListener: Listener)
    fun removeListener(modelListener: Listener)

    interface Listener {
      fun onDatabaseAdded(database: SqliteDatabase)
      fun onDatabaseRemoved(database: SqliteDatabase)
    }
  }

  interface TabController : Disposable {
    fun refreshData(): ListenableFuture<Unit>
  }
}

sealed class TabId {
  data class TableTab(val database: SqliteDatabase, val tableName: String) : TabId()
  class AdHocQueryTab : TabId()
}