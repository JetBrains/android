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
import com.android.tools.idea.concurrency.cancelOnDispose
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.sqlLanguage.hasParsingError
import com.android.tools.idea.sqlite.ui.mainView.DatabaseDiffOperation
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executor

/**
 * Implementation of the application logic related to running queries and updates on a sqlite database.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
@UiThread
class SqliteEvaluatorController(
  private val project: Project,
  private val model: DatabaseInspectorController.Model,
  private val view: SqliteEvaluatorView,
  override val closeTabInvoked: () -> Unit,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController.TabController {
  private var currentTableController: TableController? = null
  private val sqliteEvaluatorViewListener: SqliteEvaluatorView.Listener = SqliteEvaluatorViewListenerImpl()
  private val listeners = mutableListOf<Listener>()

  private val modelListener = object : DatabaseInspectorController.Model.Listener {
    private var currentDatabases = listOf<SqliteDatabase>()

    override fun onChanged(newDatabases: List<SqliteDatabase>) {
      val sortedNewDatabase = newDatabases.sortedBy { it.name }

      val toAdd = sortedNewDatabase
        .filter { !currentDatabases.contains(it) }
        .map { DatabaseDiffOperation.AddDatabase(it, model.getDatabaseSchema(it)!!, sortedNewDatabase.indexOf(it)) }
      val toRemove = currentDatabases.filter { !sortedNewDatabase.contains(it) }.map { DatabaseDiffOperation.RemoveDatabase(it) }

      view.updateDatabases(toAdd + toRemove)

      currentDatabases = newDatabases
    }
  }

  fun setUp() {
    view.addListener(sqliteEvaluatorViewListener)
    view.tableView.setEditable(false)
    model.addListener(modelListener)
  }

  /**
   * Notifies the controller that the schema associated with [database] has changed.
   */
  fun schemaChanged(database: SqliteDatabase) {
    view.schemaChanged(database)
  }

  override fun refreshData(): ListenableFuture<Unit> {
    return currentTableController?.refreshData() ?: Futures.immediateFuture(Unit)
  }

  override fun notifyDataMightBeStale() {
    currentTableController?.notifyDataMightBeStale()
  }

  override fun dispose() {
    view.removeListener(sqliteEvaluatorViewListener)
    listeners.clear()
    model.removeListener(modelListener)
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

  fun evaluateSqlStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    view.showSqliteStatement(sqliteStatement.sqliteStatementWithInlineParameters)
    view.selectDatabase(database)
    return execute(database, sqliteStatement)
  }

  fun evaluateSqlStatement(database: SqliteDatabase, sqliteStatement: String): ListenableFuture<Unit> {
    return evaluateSqlStatement(database, createSqliteStatement(project, sqliteStatement))
  }

  private fun execute(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    resetTable()

    return if (
      sqliteStatement.statementType == SqliteStatementType.SELECT ||
      sqliteStatement.statementType == SqliteStatementType.EXPLAIN
    ) {
      runQuery(database, sqliteStatement)
    }
    else {
      runUpdate(database, sqliteStatement)
    }
  }

  private fun resetTable() {
    if (currentTableController != null) {
      Disposer.dispose(currentTableController!!)
    }
    view.tableView.resetView()
  }

  private fun runQuery(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    currentTableController = TableController(
      closeTabInvoked = closeTabInvoked,
      project = project,
      view = view.tableView,
      tableSupplier = { null },
      databaseConnection = database.databaseConnection,
      sqliteStatement = sqliteStatement,
      edtExecutor = edtExecutor,
      taskExecutor = taskExecutor
    )
    Disposer.register(this@SqliteEvaluatorController, currentTableController!!)
    return currentTableController!!.setUp()
  }

  private fun runUpdate(database: SqliteDatabase, sqliteStatement: SqliteStatement): ListenableFuture<Unit> {
    return database.databaseConnection.execute(sqliteStatement)
      .transform(edtExecutor) {
        view.tableView.setEmptyText("The statement was run successfully.")
        listeners.forEach { it.onSqliteStatementExecuted(database) }
      }.catching(edtExecutor, Throwable::class.java) { throwable ->
        view.tableView.setEmptyText("An error occurred while running the statement.")
        view.tableView.reportError("Error executing SQLite statement", throwable)
      }.cancelOnDispose(this)
  }

  private inner class SqliteEvaluatorViewListenerImpl : SqliteEvaluatorView.Listener {
    override fun evaluateSqliteStatementActionInvoked(database: SqliteDatabase, sqliteStatement: String) {
      DatabaseInspectorAnalyticsTracker.getInstance(project).trackStatementExecuted(
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.USER_DEFINED_STATEMENT_CONTEXT
      )

      evaluateSqlStatement(database, sqliteStatement)
    }

    override fun sqliteStatementTextChangedInvoked(newSqliteStatement: String) {
      view.setRunSqliteStatementEnabled(!hasParsingError(project, newSqliteStatement))
    }
  }

  interface Listener {
    /**
     * Called when an user-defined SQLite statement is successfully executed
     * @param database The database on which the statement was executed.
     * */
    fun onSqliteStatementExecuted(database: SqliteDatabase)
  }
}