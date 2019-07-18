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
import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.SqliteServiceFactory
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactory
import com.android.tools.idea.sqlite.ui.mainView.SqliteView
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewListener
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewListener
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import java.util.concurrent.Executor
import kotlin.properties.Delegates

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
) : Disposable {
  companion object {
    private val logger = Logger.getInstance(SqliteController::class.java)
  }

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

  private lateinit var sqliteService: SqliteService

  private var sqliteSchema: SqliteSchema? by Delegates.observable<SqliteSchema?>(null) { _, _, newValue ->
    logger.info("Schema changed $newValue")
    if (newValue != null) {
      sqliteView.displaySchema(newValue)
    }
  }

  init {
    Disposer.register(project, this)
  }

  fun setUp() {
    sqliteView.addListener(sqliteViewListener)
  }

  fun openSqliteDatabase(sqliteFile: VirtualFile) {
    sqliteService = sqliteServiceFactory.getSqliteService(sqliteFile, this, PooledThreadExecutor.INSTANCE)

    sqliteView.startLoading("Opening Sqlite database...")
    loadDbSchema()
  }

  fun hasOpenDatabase() = sqliteSchema != null

  fun runSqlStatement(query: String) {
    val sqliteEvaluatorController = openNewEvaluatorTab()
    sqliteEvaluatorController.evaluateSqlStatement(query)
  }

  override fun dispose() {
    sqliteView.removeListener(sqliteViewListener)
  }

  private fun loadDbSchema() {
    val futureSchema = taskExecutor.transformAsync(sqliteService.openDatabase()) { sqliteService.readSchema() }

    edtExecutor.addListener(futureSchema) {
      if (!Disposer.isDisposed(this@SqliteController)) {
        sqliteView.stopLoading()
      }
    }

    edtExecutor.addCallback(futureSchema, object : FutureCallback<SqliteSchema> {
      override fun onSuccess(result: SqliteSchema?) {
        result?.let(::setDatabaseSchema)
      }

      override fun onFailure(t: Throwable) {
        if (!Disposer.isDisposed(this@SqliteController)) {
          sqliteView.reportErrorRelatedToService(sqliteService, "Error opening Sqlite database", t)
        }
      }
    })
  }

  private fun setDatabaseSchema(schema: SqliteSchema) {
    if (!Disposer.isDisposed(this)) {
      sqliteSchema = schema
    }
  }

  private fun updateView() {
    edtExecutor.addCallback(sqliteService.readSchema(), object : FutureCallback<SqliteSchema> {
      override fun onSuccess(schema: SqliteSchema?) {
        schema?.let { setDatabaseSchema(it) }
      }

      override fun onFailure(t: Throwable) {
        // TODO(b/132943925)
      }
    })
  }

  private fun openNewEvaluatorTab(): SqliteEvaluatorController {
    val tabId = TabId.AdHocQueryTab()

    val sqliteEvaluatorView = viewFactory.createEvaluatorView(project)
    // TODO(b/136556640) What name should we use for these tabs?
    sqliteView.displayResultSet(tabId, "New Query", sqliteEvaluatorView.component)

    val sqliteEvaluatorController = SqliteEvaluatorController(
      this@SqliteController,
      sqliteEvaluatorView, sqliteService, edtExecutor
    ).also { it.setUp() }

    resultSetControllers[tabId] = sqliteEvaluatorController

    sqliteEvaluatorView.addListener(SqliteEvaluatorViewListenerImpl())
    return sqliteEvaluatorController
  }

  private inner class SqliteViewListenerImpl : SqliteViewListener {
    override fun tableNodeActionInvoked(table: SqliteTable) {
      val tableId = TabId.TableTab(table.name)
      if (tableId in resultSetControllers) {
        sqliteView.focusTab(tableId)
        return
      }

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

    override fun closeTableActionInvoked(tableId: TabId) {
      sqliteView.closeTab(tableId)

      val controller = resultSetControllers.remove(tableId)
      controller?.let(Disposer::dispose)
    }
  }

  private inner class SqliteEvaluatorViewListenerImpl : SqliteEvaluatorViewListener {
    override fun evaluateSqlActionInvoked(sqlStatement: String) {
      updateView()
    }
  }
}

sealed class TabId {
  data class TableTab(val tableName: String) : TabId()
  class AdHocQueryTab : TabId()
}