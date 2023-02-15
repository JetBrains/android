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
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.cancelOnDispose
import com.android.tools.idea.concurrency.finallySync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportQueryResultsDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportTableDialogParams
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.isQueryStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.ui.tableView.OrderBy
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.ViewColumn
import com.google.common.base.Functions
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin.QUERY_RESULTS_EXPORT_BUTTON
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin.TABLE_CONTENTS_EXPORT_BUTTON
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ComparatorUtil.max
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import kotlin.math.min

/**
 * Controller responsible for displaying data from a SQLite table.
 *
 * @param tableSupplier returns a [SqliteTable] instance representing the table or view associated
 * with the controller, or `null` if the controller not associated with a specific table, e.g. in
 * the case of custom queries.
 */
@UiThread
class TableController(
  project: Project,
  private var rowBatchSize: Int = 50,
  private val view: TableView,
  private val databaseId: SqliteDatabaseId,
  private val tableSupplier: () -> SqliteTable?,
  private val databaseRepository: DatabaseRepository,
  private val sqliteStatement: SqliteStatement,
  override val closeTabInvoked: () -> Unit,
  private val showExportDialog: (ExportDialogParams) -> Unit,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController.TabController {
  private lateinit var resultSet: SqliteResultSet
  private val listener = TableViewListenerImpl()
  private var orderBy: OrderBy = OrderBy.NotOrdered
  private var rowOffset = 0

  private val databaseInspectorAnalyticsTracker =
    DatabaseInspectorAnalyticsTracker.getInstance(project)

  /** The list of columns currently shown in the view */
  private var currentCols = emptyList<ResultSetSqliteColumn>()

  /** The list of rows that is currently shown in the view. */
  private var currentRows = emptyList<SqliteRow>()

  /**
   * Future corresponding to a [refreshData] operation. If the future is done, the refresh operation
   * is over.
   */
  private var refreshDataFuture: ListenableFuture<Unit> = Futures.immediateFuture(Unit)

  private var liveUpdatesEnabled = false

  fun setUp(): ListenableFuture<Unit> {
    if (databaseId !is SqliteDatabaseId.LiveSqliteDatabaseId) {
      view.setLiveUpdatesButtonState(false)
      view.setRefreshButtonState(false)
    }

    view.startTableLoading()
    return databaseRepository
      .runQuery(databaseId, sqliteStatement)
      .transformAsync(edtExecutor) { newResultSet ->
        view.setEditable(isEditable())
        view.showPageSizeValue(rowBatchSize)
        view.addListener(listener)

        resultSet = newResultSet
        Disposer.register(this, newResultSet)

        fetchAndDisplayTableData()
      }
      .cancelOnDispose(this)
  }

  override fun refreshData(): ListenableFuture<Unit> {
    if (!refreshDataFuture.isDone) return refreshDataFuture
    view.startTableLoading()
    refreshDataFuture = fetchAndDisplayTableData()
    return refreshDataFuture
  }

  override fun notifyDataMightBeStale() {
    // refresh the table, without showing a loading screen.
    if (liveUpdatesEnabled && refreshDataFuture.isDone) {
      refreshDataFuture = fetchAndDisplayTableData()
    }
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
    val fetchTableDataFuture =
      resultSet
        .columns
        .transformAsync(edtExecutor) { columns ->
          if (Disposer.isDisposed(this)) throw ProcessCanceledException()
          if (columns != currentCols) {
            // if the columns changed we cannot use the old list of rows as reference for doing the
            // diff.
            currentRows = emptyList()
          }
          currentCols = columns

          val table = tableSupplier()
          view.showTableColumns(
            columns.filter { it.name != table?.rowIdName?.stringName }.toViewColumns(table)
          )
          view.setEditable(isEditable())

          updateDataAndButtons()
        }
        .cancelOnDispose(this)

    val futureCatching = handleFetchRowsError(fetchTableDataFuture)

    val future =
      futureCatching.finallySync(edtExecutor) { view.stopTableLoading() }.cancelOnDispose(this)

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

    return fetchAndDisplayRows().transformAsync(taskExecutor) { resultSet.totalRowCount }.transform(
        edtExecutor
      ) { rowCount ->
      view.setFetchPreviousRowsButtonState(rowOffset > 0)
      view.setFetchNextRowsButtonState(rowOffset + rowBatchSize < rowCount)
    }
  }

  private fun updateDataAndButtonsWithLoadingScreens(): ListenableFuture<Unit> {
    view.startTableLoading()
    val updateDataFuture = updateDataAndButtons()
    val future =
      updateDataFuture.finallySync(edtExecutor) {
        if (Disposer.isDisposed(this@TableController)) throw ProcessCanceledException()
        view.stopTableLoading()
      }

    return handleFetchRowsError(future)
  }

  /**
   * Fetches rows through the [resultSet] using [rowOffset] and [rowBatchSize]. The view is updated
   * through a list of [RowDiffOperation]. Compared to just recreating the view this approach has
   * the advantage that the state is not lost. Eg. if the user is navigating the table using the
   * keyboard we don't want to lose the navigation each time the data has to be updated.
   */
  private fun fetchAndDisplayRows(): ListenableFuture<Unit> {
    return resultSet
      .getRowBatch(rowOffset, rowBatchSize)
      .transform(edtExecutor) { newRows ->
        val rowDiffOperations = mutableListOf<RowDiffOperation>()

        // Update the cells that already exist
        for (rowIndex in 0 until min(currentRows.size, newRows.size)) {
          val rowCellUpdates = performRowsDiff(currentRows[rowIndex], newRows[rowIndex], rowIndex)
          rowDiffOperations.addAll(rowCellUpdates)
        }

        // add new rows
        if (currentRows.size < newRows.size) {
          rowDiffOperations.addAll(
            newRows.drop(currentRows.size).map { RowDiffOperation.AddRow(it) }
          )
        }
        // remove extra rows
        else if (currentRows.size > newRows.size) {
          rowDiffOperations.add(RowDiffOperation.RemoveLastRows(newRows.size))
        }

        view.setRowOffset(rowOffset)
        view.updateRows(rowDiffOperations)
        view.setEditable(isEditable())

        currentRows = newRows
      }
      .cancelOnDispose(this)
  }

  /**
   * Returns a list of [UpdateCell] commands. A command is added to the list if [oldRow] and
   * [newRow] have different values in the same position.
   */
  private fun performRowsDiff(
    oldRow: SqliteRow,
    newRow: SqliteRow,
    rowIndex: Int
  ): List<RowDiffOperation.UpdateCell> {
    val cellUpdates = mutableListOf<RowDiffOperation.UpdateCell>()

    for (colIndex in oldRow.values.indices) {
      if (oldRow.values[colIndex] != newRow.values[colIndex]) {
        cellUpdates.add(RowDiffOperation.UpdateCell(newRow.values[colIndex], rowIndex, colIndex))
      }
    }

    return cellUpdates
  }

  private fun handleFetchRowsError(future: ListenableFuture<Unit>): ListenableFuture<Unit> {
    future.addCallback(edtExecutor, success = {}) { error ->
      if (Disposer.isDisposed(this)) return@addCallback
      view.resetView()
      if (error !is CancellationException && error !is AppInspectionConnectionException) {
        view.reportError("Error retrieving data from table.", error)
      }
    }
    return future
  }

  private fun isEditable() =
    tableSupplier() != null &&
      !liveUpdatesEnabled &&
      !(tableSupplier()?.isView ?: false) &&
      databaseId is SqliteDatabaseId.LiveSqliteDatabaseId

  private inner class TableViewListenerImpl : TableView.Listener {
    override fun toggleOrderByColumnInvoked(viewColumn: ViewColumn) {
      orderBy = orderBy.nextState(viewColumn.name)

      Disposer.dispose(resultSet)
      view.startTableLoading()

      databaseRepository
        .selectOrdered(databaseId, sqliteStatement, orderBy)
        .transform(edtExecutor) { newResultSet ->
          if (Disposer.isDisposed(this@TableController)) {
            newResultSet.dispose()
            throw ProcessCanceledException()
          }

          resultSet = newResultSet
          Disposer.register(this@TableController, newResultSet)

          rowOffset = 0
          fetchAndDisplayTableData()
        }
        .transform(edtExecutor) { view.setColumnSortIndicator(orderBy) }
    }

    override fun cancelRunningStatementInvoked() {
      val connectivityState =
        when (databaseId) {
          is SqliteDatabaseId.FileSqliteDatabaseId ->
            AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_OFFLINE
          is SqliteDatabaseId.LiveSqliteDatabaseId ->
            AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_ONLINE
        }

      databaseInspectorAnalyticsTracker.trackStatementExecutionCanceled(
        connectivityState,
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.UNKNOWN_STATEMENT_CONTEXT
      )
      // Closing a tab triggers its dispose method, which cancels the future, stopping the running
      // query.
      closeTabInvoked()
    }

    override fun rowCountChanged(rowCount: String) {
      val errorMessage = "Row count must be a positive integer."
      try {
        val intRowCount = rowCount.toInt()
        if (intRowCount <= 0) {
          view.reportError(errorMessage, null)
          return
        }

        rowBatchSize = intRowCount
        updateDataAndButtonsWithLoadingScreens()
      } catch (e: NumberFormatException) {
        view.reportError(errorMessage, null)
      }
    }

    override fun loadPreviousRowsInvoked() {
      rowOffset = max(0, rowOffset - rowBatchSize)
      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadNextRowsInvoked() {
      rowOffset += rowBatchSize
      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadFirstRowsInvoked() {
      rowOffset = 0
      updateDataAndButtonsWithLoadingScreens()
    }

    override fun loadLastRowsInvoked() {
      resultSet.totalRowCount.transformAsync(edtExecutor) { rowCount ->
        rowOffset = (rowCount / rowBatchSize) * rowBatchSize

        if (rowOffset == rowCount) rowOffset -= rowBatchSize
        updateDataAndButtonsWithLoadingScreens()
      }
    }

    override fun refreshDataInvoked() {
      databaseInspectorAnalyticsTracker.trackTargetRefreshed(
        AppInspectionEvent.DatabaseInspectorEvent.TargetType.TABLE_TARGET
      )
      refreshData()
    }

    override fun toggleLiveUpdatesInvoked() {
      liveUpdatesEnabled = !liveUpdatesEnabled
      view.setEditable(isEditable())

      if (liveUpdatesEnabled) {
        notifyDataMightBeStale()
      }

      databaseInspectorAnalyticsTracker.trackLiveUpdatedToggled(liveUpdatesEnabled)
    }

    override fun showExportToFileDialogInvoked() {
      val tableName = tableSupplier()?.name
      val exportScenario: ExportDialogParams =
        when {
          tableName != null ->
            ExportTableDialogParams(databaseId, tableName, TABLE_CONTENTS_EXPORT_BUTTON)
          sqliteStatement.isQueryStatement ->
            ExportQueryResultsDialogParams(databaseId, sqliteStatement, QUERY_RESULTS_EXPORT_BUTTON)
          else ->
            return // TODO(161081452): consider throwing an Exception or logging an error instead of
        // silently ignoring the request
        }

      this@TableController.showExportDialog(exportScenario)
    }

    override fun updateCellInvoked(
      targetRowIndex: Int,
      targetColumn: ViewColumn,
      newValue: SqliteValue
    ) {
      val targetTable = tableSupplier()
      if (targetTable == null) {
        view.reportError("Can't update. Table not found.", null)
        return
      }

      view.startTableLoading()
      val targetRow = currentRows[targetRowIndex]
      databaseRepository
        .updateTable(databaseId, targetTable, targetRow, targetColumn.name, newValue)
        .addCallback(
          edtExecutor,
          object : FutureCallback<Unit> {
            override fun onSuccess(result: Unit?) {
              databaseInspectorAnalyticsTracker.trackTableCellEdited()
              refreshData()
            }

            override fun onFailure(t: Throwable) {
              view.revertLastTableCellEdit()
              view.stopTableLoading()
              view.reportError("Can't execute update: ", t)
            }
          }
        )
    }
  }

  private fun List<ResultSetSqliteColumn>.toViewColumns(table: SqliteTable? = null) = map {
    it.toViewColumn(table)
  }

  /**
   * Column information in [ResultSetSqliteColumn] can be incomplete. This method tries to overlap
   * the information from the column with the information from the schema.
   */
  private fun ResultSetSqliteColumn.toViewColumn(table: SqliteTable? = null): ViewColumn {
    val schemaColumn = table?.columns?.firstOrNull { it.name == name }
    return ViewColumn(
      name,
      schemaColumn?.inPrimaryKey ?: inPrimaryKey ?: false,
      schemaColumn?.isNullable ?: isNullable ?: true
    )
  }
}
