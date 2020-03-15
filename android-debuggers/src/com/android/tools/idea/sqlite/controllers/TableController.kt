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
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.finallySync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.transform
import com.android.tools.idea.sqlite.sqlLanguage.inlineParameterValues
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.google.common.base.Functions
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ComparatorUtil.max
import java.util.LinkedList
import java.util.concurrent.Executor
import kotlin.math.min

/**
 * Controller specialized in displaying rows and columns from a [SqliteResultSet].
 *
 * The ownership of the [SqliteResultSet] is transferred to the [TableController],
 * i.e. it is closed when [dispose] is called.
 *
 * The [SqliteResultSet] is not necessarily associated with a real table in the database, in those cases [TableInfo] will be null.
 */
@UiThread
class TableController(
  private val project: Project,
  private var rowBatchSize: Int = 50,
  private val view: TableView,
  private val tableSupplier: () -> SqliteTable?,
  private val databaseConnection: DatabaseConnection,
  private val sqliteStatement: SqliteStatement,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController.TabController {
  private lateinit var resultSet: SqliteResultSet
  private val listener = TableViewListenerImpl()
  private var orderBy: OrderBy? = null
  private var start = 0

  private var lastExecutedQuery = sqliteStatement

  /**
   * The list of rows that is currently shown in the view.
   */
  private var currentRows = emptyList<SqliteRow>()

  fun setUp(): ListenableFuture<Unit> {
    view.startTableLoading()
    return databaseConnection.execute(sqliteStatement).transform(edtExecutor) { newResultSet ->
      lastExecutedQuery = sqliteStatement

      if (Disposer.isDisposed(this)) {
        Disposer.dispose(newResultSet)
        throw ProcessCanceledException()
      }

      view.setEditable(isEditable())
      view.showPageSizeValue(rowBatchSize)
      view.addListener(listener)

      resultSet = newResultSet
      Disposer.register(this, newResultSet)

      fetchAndDisplayTableData()

      return@transform
    }
  }

  override fun refreshData(): ListenableFuture<Unit> {
    view.startTableLoading()
    return fetchAndDisplayTableData()
  }

  override fun dispose() {
    view.stopTableLoading()
    view.removeListener(listener)
  }

  /**
   * Gets columns and rows from [resultSet] and updates the view.
   *
   * Callers of this method should take care of setting the view in a loading state.
   */
  private fun fetchAndDisplayTableData(): ListenableFuture<Unit> {
    val fetchTableDataFuture = resultSet.columns.transformAsync(edtExecutor) { columns ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      val table = tableSupplier()
      view.showTableColumns(columns.filter { it.name != table?.rowIdName?.stringName })
      view.setEditable(isEditable())

      updateDataAndButtons()
    }

    val futureCatching = handleFetchRowsError(fetchTableDataFuture)

    val future = futureCatching.finallySync(edtExecutor) {
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      view.stopTableLoading()
    }

    return Futures.transform(future, Functions.constant(Unit), MoreExecutors.directExecutor())
  }

  /**
   * Calls [fetchAndDisplayRows] to fetch new data and updates the view.
   *
   * This method doesn't set the view in a loading state.
   */
  private fun updateDataAndButtons(): ListenableFuture<Unit> {
    view.setFetchPreviousRowsButtonState(false)
    view.setFetchNextRowsButtonState(false)

    return fetchAndDisplayRows()
      .transformAsync(taskExecutor) {
        resultSet.totalRowCount
      }.transform(edtExecutor) { rowCount ->
        view.setFetchPreviousRowsButtonState(start > 0)
        view.setFetchNextRowsButtonState(start+rowBatchSize < rowCount)
      }
  }

  private fun updateDataAndButtonsWithLoadingScreens(): ListenableFuture<Unit> {
    view.startTableLoading()
    val updateDataFuture = updateDataAndButtons()
    val future = updateDataFuture.finallySync(edtExecutor) {
      if (Disposer.isDisposed(this@TableController)) throw ProcessCanceledException()
      view.stopTableLoading()
    }

    return handleFetchRowsError(future)
  }

  /**
   * Fetches rows through the [resultSet] using [start] and [rowBatchSize].
   * The view is updated through a list of [RowDiffOperation]. Compared to just recreating the view
   * this approach has the advantage that the state is not lost. Eg. if the user is navigating the table
   * using the keyboard we don't want to lose the navigation each time the data has to be updated.
   */
  private fun fetchAndDisplayRows() : ListenableFuture<Unit> {
    return resultSet.getRowBatch(start, rowBatchSize).transform(edtExecutor) { newRows ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()

      val rowDiffOperations = mutableListOf<RowDiffOperation>()

      // Update the cells that already exist
      for (rowIndex in 0 until min(currentRows.size, newRows.size)) {
        val rowCellUpdates = performRowsDiff(currentRows[rowIndex], newRows[rowIndex], rowIndex)
        rowDiffOperations.addAll(rowCellUpdates)
      }

      // add new rows
      if (currentRows.size < newRows.size) {
        rowDiffOperations.addAll(newRows.drop(currentRows.size).map { RowDiffOperation.AddRow(it) })
      }
      // remove extra rows
      else if (currentRows.size > newRows.size) {
        rowDiffOperations.add(RowDiffOperation.RemoveLastRows(newRows.size))
      }

      view.updateRows(rowDiffOperations)
      view.setEditable(isEditable())

      currentRows = newRows
    }
  }

  /**
   * Returns a list of [UpdateCell] commands.
   * A command is added to the list if [oldRow] and [newRow] have different values in the same position.
   */
  private fun performRowsDiff(oldRow: SqliteRow, newRow: SqliteRow, rowIndex: Int): List<RowDiffOperation.UpdateCell> {
    val cellUpdates = mutableListOf<RowDiffOperation.UpdateCell>()

    for (colIndex in oldRow.values.indices) {
      if (oldRow.values[colIndex] != newRow.values[colIndex]) {
        cellUpdates.add(RowDiffOperation.UpdateCell(newRow.values[colIndex], rowIndex, colIndex))
      }
    }

    return cellUpdates
  }

  private fun handleFetchRowsError(future: ListenableFuture<Unit>): ListenableFuture<Unit> {
    return future.catching(edtExecutor, Throwable::class.java) { error ->
      if (Disposer.isDisposed(this)) throw ProcessCanceledException()
      view.resetView()
      view.reportError("Error retrieving data from table.", error)
    }
  }

  private fun isEditable() = tableSupplier() != null

  private inner class TableViewListenerImpl : TableView.Listener {
    override fun toggleOrderByColumnInvoked(sqliteColumn: SqliteColumn) {
      if (orderBy != null && orderBy!!.column == sqliteColumn) {
        orderBy = OrderBy(sqliteColumn, !orderBy!!.asc)
      } else {
        orderBy = OrderBy(sqliteColumn, true)
      }

      val order = if (orderBy!!.asc) "ASC" else "DESC"
      val selectOrderByStatement = sqliteStatement.transform {
        "SELECT * FROM ($it) ORDER BY ${AndroidSqlLexer.getValidName(orderBy!!.column.name)} $order"
      }

      Disposer.dispose(resultSet)

      view.startTableLoading()
      databaseConnection.execute(selectOrderByStatement).transform(edtExecutor) { newResultSet ->
        lastExecutedQuery = selectOrderByStatement

        if (Disposer.isDisposed(this@TableController)) {
          newResultSet.dispose()
          throw ProcessCanceledException()
        }

        resultSet = newResultSet
        Disposer.register(this@TableController, newResultSet)

        start = 0
        fetchAndDisplayTableData()
      }
    }

    override fun cancelRunningStatementInvoked() {
      // TODO(b/151204958): cancel future
    }

    override fun rowCountChanged(rowCount: Int) {
      if (rowCount < 0) {
        view.reportError("Row count must be non-negative", null)
        return
      }

      rowBatchSize = rowCount

      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadPreviousRowsInvoked() {
      start = max(0, start-rowBatchSize)
      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadNextRowsInvoked() {
      start += rowBatchSize
      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadFirstRowsInvoked() {
      start = 0
      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadLastRowsInvoked() {
      resultSet.totalRowCount.transformAsync(edtExecutor) { rowCount ->
        start = (rowCount / rowBatchSize) * rowBatchSize

        if (start == rowCount) start -= rowBatchSize
        updateDataAndButtonsWithLoadingScreens()
      }
    }

    override fun refreshDataInvoked() {
      refreshData()
    }

    override fun updateCellInvoked(targetRowIndex: Int, targetColumn: SqliteColumn, newValue: SqliteValue) {
      val table = tableSupplier()
      if (table == null) {
        view.reportError("Can't update. Table not found.", null)
        return
      }

      val targetRow = currentRows[targetRowIndex]
      val rowIdColumnValue = targetRow.values.firstOrNull { it.columnName == table.rowIdName?.stringName }

      val parametersValues = mutableListOf(newValue)

      val whereExpression = if (rowIdColumnValue != null) {
        parametersValues.add(rowIdColumnValue.value)
        "${AndroidSqlLexer.getValidName(rowIdColumnValue.columnName)} = ?"
      } else {
        val tablePrimaryKeyNames = table.columns.filter { it.inPrimaryKey }.map { it.name }
        val expression = targetRow.values
          .filter { tablePrimaryKeyNames.contains(it.columnName) }
          .onEach { parametersValues.add(it.value) }
          .joinToString(separator = " AND ") {
            "${AndroidSqlLexer.getValidName(it.columnName)} = ?"
          }

        // check that all columns in the primary key have been used
        if (parametersValues.size - 1 != tablePrimaryKeyNames.size) "" else expression
      }

      if (whereExpression.isEmpty()) {
        view.reportError("Can't update. No primary keys or rowid column.", null)
        return
      }

      val updateStatement =
        "UPDATE ${AndroidSqlLexer.getValidName(table.name)} " +
        "SET ${AndroidSqlLexer.getValidName(targetColumn.name)} = ? " +
        "WHERE $whereExpression"

      val psiElement = AndroidSqlParserDefinition.parseSqlQuery(project, updateStatement)
      val updateStatementStringRepresentation = inlineParameterValues(psiElement, LinkedList(parametersValues))

      databaseConnection.execute(SqliteStatement(updateStatement, parametersValues, updateStatementStringRepresentation))
        .addCallback(edtExecutor, object : FutureCallback<SqliteResultSet> {
          override fun onSuccess(result: SqliteResultSet?) {
            refreshData()
          }

          override fun onFailure(t: Throwable) {
            view.reportError("Can't execute update", t)
          }
        })
    }
  }
}

data class OrderBy(val column: SqliteColumn, val asc: Boolean)

data class TableInfo(val database: SqliteDatabase, val tableName: String)