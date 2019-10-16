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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.refEq
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.mocks.MockTableView
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.ui.tableView.TableViewListener
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.InOrder
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.sql.JDBCType

//TODO(b/131589065) write tests for listOfSqliteColumns of different sizes, to handle all paths of ResultSetController.fetchRowBatch()
class ResultSetControllerTest : UsefulTestCase() {

  private lateinit var tableView: MockTableView
  private lateinit var sqliteResultSet: SqliteResultSet
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var resultSetController: ResultSetController
  private lateinit var orderVerifier: InOrder

  private val listOfSqliteColumns = listOf(
    SqliteColumn("col1", JDBCType.VARCHAR),
    SqliteColumn("col2", JDBCType.INTEGER),
    SqliteColumn("col2", JDBCType.CHAR)
  )

  private val listOfSqliteRows = listOf(
    SqliteRow(listOf(
      SqliteColumnValue(listOfSqliteColumns[0], "aString"),
      SqliteColumnValue(listOfSqliteColumns[1], 0),
      SqliteColumnValue(listOfSqliteColumns[2], 'c')
    ))
  )

  override fun setUp() {
    super.setUp()
    tableView = spy(MockTableView::class.java)
    sqliteResultSet = mock(SqliteResultSet::class.java)
    edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())

    orderVerifier = inOrder(sqliteResultSet, tableView)
  }

  fun testSetUp() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    `when`(sqliteResultSet.nextRowBatch()).thenReturn(Futures.immediateFuture(listOfSqliteRows))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(listOfSqliteColumns)
    orderVerifier.verify(tableView).showTableRowBatch(listOfSqliteRows)
    orderVerifier.verify(tableView).stopTableLoading()

    verify(sqliteResultSet).rowBatchSize = anyInt()
    verify(sqliteResultSet).columns

    verify(tableView, Mockito.times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testSetUpTableNameIsNull() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, null, sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()

    // Assert
    verify(tableView).startTableLoading()
  }

  fun testSetUpError() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFailedFuture(throwable))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView)
      .reportError(eq("Error retrieving rows for table \"tableName\""), refEq(throwable))
    orderVerifier.verify(tableView).stopTableLoading()
  }

  fun testSetUpIsDisposed() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    Disposer.dispose(resultSetController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).addListener(any(TableViewListener::class.java))
    verify(tableView).showRowCount(any(Int::class.java))
    verify(tableView).startTableLoading()
    verify(tableView).removeListener(any(TableViewListener::class.java))
    verifyNoMoreInteractions(tableView)
  }

  fun testSetUpErrorIsDisposed() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFailedFuture(Throwable()))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act/Assert
    Disposer.dispose(resultSetController)
    assertThrows<ProcessCanceledException>(ProcessCanceledException::class.java) { resultSetController.setUp() }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  fun testFetchNextRowsUiIsDisabledWhenNoMoreRowsAvailableOnSetup() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    `when`(sqliteResultSet.nextRowBatch())
      .thenReturn(Futures.immediateFuture(listOfSqliteRows))
      .thenReturn(Futures.immediateFuture(emptyList()))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).setFetchNextRowsButtonState(false)
  }

  fun testFetchNextRowsUiIsDisabledWhenNoMoreRowsAvailableOnFetchNextRows() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    `when`(sqliteResultSet.nextRowBatch())
      .thenReturn(Futures.immediateFuture(listOfSqliteRows))
      .thenReturn(Futures.immediateFuture(listOfSqliteRows))
      .thenReturn(Futures.immediateFuture(emptyList()))
    resultSetController = ResultSetController(testRootDisposable, 1, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).showTableRowBatch(listOfSqliteRows)
    verify(tableView).setFetchNextRowsButtonState(false)
  }
}
