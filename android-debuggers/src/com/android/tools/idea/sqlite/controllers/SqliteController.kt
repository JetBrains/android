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
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.SqliteServiceFactory
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactory
import com.android.tools.idea.sqlite.ui.mainView.SqliteView
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewListener
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.text.trimMiddle
import org.jetbrains.ide.PooledThreadExecutor
import java.util.TreeMap
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Implementation of the application logic related to viewing/editing sqlite databases.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteController(
  private val project: Project,
  private val model: Model,
  private val sqliteServiceFactory: SqliteServiceFactory,
  private val viewFactory: SqliteEditorViewFactory,
  val sqliteView: SqliteView,
  val fileOpener: Consumer<VirtualFile>,
  edtExecutor: EdtExecutorService,
  taskExecutor: Executor
) : Disposable {
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

  private val virtualFileListener: BulkFileListener

  init {
    Disposer.register(project, this)

    virtualFileListener = object : BulkFileListener {
      override fun before(events: MutableList<out VFileEvent>) {
        val openDatabases = model.openDatabases.keys

        if (openDatabases.isEmpty()) return

        val toClose = mutableListOf<SqliteDatabase>()
        for (event in events) {
          if (event !is VFileDeleteEvent) continue

          for (database in openDatabases) {
            if (VfsUtil.isAncestor(event.file, database.virtualFile, false)) {
              toClose.add(database)
            }
          }
        }

        toClose.forEach { closeDatabase(it) }
      }
    }

    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, virtualFileListener)
  }

  fun setUp() {
    sqliteView.addListener(sqliteViewListener)
  }

  fun openSqliteDatabase(sqliteFile: VirtualFile) {
    val sqliteService = sqliteServiceFactory.getSqliteService(sqliteFile, PooledThreadExecutor.INSTANCE)
    val database = SqliteDatabase(sqliteFile, sqliteService)
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

  fun runSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement) {
    val sqliteEvaluatorController = openNewEvaluatorTab()
    sqliteEvaluatorController.evaluateSqlStatement(database, sqliteStatement)
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
    val index = model.getSortedIndexOf(database)
    sqliteView.addDatabaseSchema(database, sqliteSchema, index)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.addDatabase(database, index) }
    model.add(database, sqliteSchema)
  }

  private fun closeDatabase(database: SqliteDatabase) {
    val tabsToClose = resultSetControllers.keys
      .filterIsInstance<TabId.TableTab>()
      .filter { it.database == database }

    tabsToClose.forEach { closeTab(it) }

    val index = model.getSortedIndexOf(database)
    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeDatabase(index) }

    sqliteView.removeDatabaseSchema(database)

    model.remove(database)
    Disposer.dispose(database)
  }

  private fun closeTab(tabId: TabId) {
    sqliteView.closeTab(tabId)
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
    sqliteView.updateDatabase(database, toAdd)
  }

  private fun openNewEvaluatorTab(): SqliteEvaluatorController {
    val tabId = TabId.AdHocQueryTab()

    val sqliteEvaluatorView = viewFactory.createEvaluatorView(
      project,
      object : SchemaProvider { override fun getSchema(database: SqliteDatabase) = model.openDatabases[database] },
      viewFactory.createTableView()
    )

    // TODO(b/136556640) What name should we use for these tabs?
    sqliteView.openTab(tabId, "New Query", sqliteEvaluatorView.component)

    val sqliteEvaluatorController = SqliteEvaluatorController(
      this@SqliteController,
      sqliteEvaluatorView,
      edtExecutor
    ).also { it.setUp() }
    sqliteEvaluatorController.addListener(SqliteEvaluatorControllerListenerImpl())

    resultSetControllers[tabId] = sqliteEvaluatorController

    model.openDatabases.keys.forEachIndexed { index, sqliteDatabase ->
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

      val tableView = viewFactory.createTableView()
      sqliteView.openTab(tableId, table.name, tableView.component)

      val tableController = TableController(
        parentDisposable = this@SqliteController,
        view = tableView,
        tableName = table.name,
        sqliteService = sqliteService,
        sqliteStatement = SqliteStatement("SELECT * FROM ${AndroidSqlLexer.getValidName(table.name)}"),
        edtExecutor = edtExecutor
      )

      edtExecutor.addCallback(tableController.setUp(), object : FutureCallback<Unit> {
        override fun onSuccess(result: Unit?) {
          resultSetControllers[tableId] = tableController
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

    override fun syncDatabaseActionInvoked(database: SqliteDatabase) {
      val deviceFileId: DeviceFileId = database.virtualFile.getUserData(DeviceFileId.KEY) ?: return
      val downloadFuture = DeviceFileDownloaderService.getInstance(project).downloadFile(deviceFileId, object : DownloadProgress {
        override val isCancelled: Boolean
          get() = false

        override fun onStarting(entryFullPath: String) {
          sqliteView.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: start sync")
        }

        override fun onProgress(entryFullPath: String, currentBytes: Long, totalBytes: Long) {
          sqliteView.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: sync progress $currentBytes/$totalBytes")
        }

        override fun onCompleted(entryFullPath: String) {
          sqliteView.reportSyncProgress("${entryFullPath.trimMiddle(20, true)}: sync completed")
        }
      })

      edtExecutor.transform(downloadFuture) { downloadedFileData ->
        fileOpener.accept(downloadedFileData.virtualFile)
        sqliteView.reportSyncProgress("")
      }
    }
  }

  private inner class SqliteEvaluatorControllerListenerImpl : SqliteEvaluatorControllerListener {
    override fun onSchemaUpdated(database: SqliteDatabase) {
      updateDatabaseSchema(database)
    }
  }

  /**
   * Thread safe model for Database Inspector. Used to store and access currently open [SqliteDatabase]s and their [SqliteSchema]s.
   */
  interface Model {
    /**
     * A set of open databases sorted in alphabetical order by the name of the database.
     */
    val openDatabases: TreeMap<SqliteDatabase, SqliteSchema>

    fun getSortedIndexOf(database: SqliteDatabase): Int
    fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema)
    fun remove(database: SqliteDatabase)
  }
}

sealed class TabId {
  data class TableTab(val database: SqliteDatabase, val tableName: String) : TabId()
  class AdHocQueryTab : TabId()
}