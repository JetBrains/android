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
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.util.Disposer

/**
 * Implementation of the application logic related to running queries and updates on a sqlite database.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteEvaluatorController(
  private val view: SqliteEvaluatorView,
  private val edtExecutor: FutureCallbackExecutor
) : DatabaseInspectorController.TabController {
  private var currentTableController: TableController? = null
  private val sqliteEvaluatorViewListener: SqliteEvaluatorView.Listener = SqliteEvaluatorViewListenerImpl()
  private val listeners = mutableListOf<Listener>()

  fun setUp() {
    view.addListener(sqliteEvaluatorViewListener)
  }

  fun removeDatabase(index: Int) {
    view.removeDatabase(index)
  }

  override fun refreshData(): ListenableFuture<Unit> {
    return currentTableController?.refreshData() ?: Futures.immediateFuture(Unit)
  }

  override fun dispose() {
    view.removeListener(sqliteEvaluatorViewListener)
    listeners.clear()
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  fun removeListeners() {
    listeners.clear()
  }

  fun addDatabase(database: SqliteDatabase, index: Int) {
    view.addDatabase(database, index)
  }

  fun evaluateSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    view.showSqliteStatement(sqliteStatement.assignValuesToParameters())
    view.selectDatabase(database)
    return execute(database, sqliteStatement)
  }

  private fun execute(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    val settableFuture = SettableFuture.create<Unit>()
    val databaseConnection = database.databaseConnection
    edtExecutor.addCallback(databaseConnection.execute(sqliteStatement), object : FutureCallback<SqliteResultSet?> {
      override fun onSuccess(resultSet: SqliteResultSet?) {
        if (resultSet != null) {
          // query statement
          currentTableController = TableController(
            view = view.tableView,
            table = null,
            databaseConnection = databaseConnection,
            sqliteStatement = sqliteStatement,
            edtExecutor = edtExecutor
          )
          Disposer.register(this@SqliteEvaluatorController, currentTableController!!)
          currentTableController!!.setUp()
        } else {
          // update statement
          view.tableView.resetView()
          view.tableView.setEditable(false)
          listeners.forEach { it.onSchemaUpdated(database) }
        }

        settableFuture.set(Unit)
      }

      override fun onFailure(t: Throwable) {
        view.tableView.reportError("Error executing SQLite statement", t)
        settableFuture.setException(t)
      }
    })

    return settableFuture
  }

  private inner class SqliteEvaluatorViewListenerImpl : SqliteEvaluatorView.Listener {
    override fun evaluateSqlActionInvoked(database: SqliteDatabase, sqliteStatement: String) {
      // TODO(b/143341562) handle SQLite statements with templates for ad-hoc queries.
      evaluateSqlStatement(database, SqliteStatement(sqliteStatement, emptyList()))
    }
  }

  interface Listener {
    fun onSchemaUpdated(database: SqliteDatabase)
  }
}
