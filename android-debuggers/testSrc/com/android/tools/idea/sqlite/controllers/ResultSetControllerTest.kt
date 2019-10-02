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

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.refEq
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.sql.JDBCType

//TODO(b/131589065) write tests for listOfSqliteColumns of different sizes, to handle all paths of ResultSetController.fetchRowBatch()
class ResultSetControllerTest : UsefulTestCase() {

  private lateinit var tableView: TableView
  private lateinit var sqliteResultSet: SqliteResultSet
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var resultSetController: ResultSetController
  private lateinit var orderVerifier: InOrder

  private val listOfSqliteColumns = listOf(
    SqliteColumn("col1", JDBCType.VARCHAR),
    SqliteColumn("col2", JDBCType.ARRAY),
    SqliteColumn("col2", JDBCType.CHAR)
  )

  override fun setUp() {
    super.setUp()
    tableView = mock(TableView::class.java)
    sqliteResultSet = mock(SqliteResultSet::class.java)
    edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())

    orderVerifier = inOrder(sqliteResultSet, tableView)
  }

  fun testSetUp() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(listOfSqliteColumns)
    orderVerifier.verify(tableView).stopTableLoading()

    verify(sqliteResultSet).rowBatchSize = anyInt()
    verify(sqliteResultSet).columns
  }

  fun testSetUpTableNameIsNull() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, tableView, null, sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()

    // Assert
    verify(tableView).startTableLoading()
  }

  fun testSetUpError() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFailedFuture(throwable))
    resultSetController = ResultSetController(testRootDisposable, tableView, "tableName", sqliteResultSet, edtExecutor)

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
    resultSetController = ResultSetController(testRootDisposable, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    Disposer.dispose(resultSetController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).startTableLoading()
    verifyNoMoreInteractions(tableView)
  }

  fun testSetUpErrorIsDisposed() {
    // Prepare
    `when`(sqliteResultSet.columns).thenReturn(Futures.immediateFailedFuture(Throwable()))
    resultSetController = ResultSetController(testRootDisposable, tableView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    Disposer.dispose(resultSetController)
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).startTableLoading()
    verifyNoMoreInteractions(tableView)
  }
}
