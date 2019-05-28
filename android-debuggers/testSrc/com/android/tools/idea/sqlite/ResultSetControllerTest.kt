/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite

import com.android.tools.idea.concurrent.FutureCallbackExecutor
import com.android.tools.idea.sqlite.controllers.ResultSetController
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteResultSet
import com.android.tools.idea.sqlite.ui.ResultSetView
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.isNull
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.sql.JDBCType

//TODO(b/131589065) write tests for listOfSqliteColumns of different sizes, to handle all paths of ResultSetController.fetchRowBatch()
class ResultSetControllerTest : UsefulTestCase() {

  private lateinit var resultSetView: ResultSetView
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
    resultSetView = mock(ResultSetView::class.java)
    sqliteResultSet = mock(SqliteResultSet::class.java)
    edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())

    orderVerifier = inOrder(sqliteResultSet, resultSetView)
  }

  fun testSetUp() {
    // Prepare
    `when`(sqliteResultSet.columns()).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, resultSetView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(resultSetView).startTableLoading("tableName")
    orderVerifier.verify(resultSetView).showTableColumns(listOfSqliteColumns)
    orderVerifier.verify(resultSetView).stopTableLoading()

    verify(sqliteResultSet).rowBatchSize = anyInt()
    verify(sqliteResultSet).columns()
  }

  fun testSetUpTableNameIsNull() {
    // Prepare
    `when`(sqliteResultSet.columns()).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, resultSetView, null, sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()

    // Assert
    verify(resultSetView).startTableLoading(isNull())
  }

  fun testSetUpError() {
    // Prepare
    val throwable = Throwable()
    `when`(sqliteResultSet.columns()).thenReturn(Futures.immediateFailedFuture(throwable))
    resultSetController = ResultSetController(testRootDisposable, resultSetView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(resultSetView).startTableLoading("tableName")
    orderVerifier.verify(resultSetView)
      .reportErrorRelatedToTable(eq("tableName"), eq("Error retrieving contents of tableName"), refEq(throwable))
    orderVerifier.verify(resultSetView).stopTableLoading()
  }

  fun testSetUpIsDisposed() {
    // Prepare
    `when`(sqliteResultSet.columns()).thenReturn(Futures.immediateFuture(listOfSqliteColumns))
    resultSetController = ResultSetController(testRootDisposable, resultSetView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    Disposer.dispose(resultSetController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(resultSetView).startTableLoading("tableName")
    verifyNoMoreInteractions(resultSetView)
  }

  fun testSetUpErrorIsDisposed() {
    // Prepare
    `when`(sqliteResultSet.columns()).thenReturn(Futures.immediateFailedFuture(Throwable()))
    resultSetController = ResultSetController(testRootDisposable, resultSetView, "tableName", sqliteResultSet, edtExecutor)

    // Act
    Disposer.dispose(resultSetController)
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(resultSetView).startTableLoading("tableName")
    verifyNoMoreInteractions(resultSetView)
  }
}
