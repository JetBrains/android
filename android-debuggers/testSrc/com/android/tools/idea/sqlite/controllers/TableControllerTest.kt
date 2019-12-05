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
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.jdbc.JdbcDatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.mocks.MockSqliteResultSet
import com.android.tools.idea.sqlite.mocks.MockTableView
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.sql.DriverManager
import java.sql.JDBCType

class TableControllerTest : PlatformTestCase() {
  private lateinit var tableView: MockTableView
  private lateinit var mockResultSet: MockSqliteResultSet
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var tableController: TableController
  private lateinit var mockDatabaseConnection: DatabaseConnection

  private lateinit var orderVerifier: InOrder

  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var realDatabaseConnection: DatabaseConnection

  private lateinit var authorIdColumn: SqliteColumn
  private lateinit var authorsRow1: SqliteRow
  private lateinit var authorsRow2: SqliteRow
  private lateinit var authorsRow4: SqliteRow
  private lateinit var authorsRow5: SqliteRow

  override fun setUp() {
    super.setUp()
    tableView = spy(MockTableView::class.java)
    mockResultSet = MockSqliteResultSet()
    edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
    mockDatabaseConnection = mock(DatabaseConnection::class.java)
    orderVerifier = inOrder(tableView)

    sqliteUtil = SqliteTestUtil(
      IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    val sqliteFile = sqliteUtil.createTestSqliteDatabase()
    realDatabaseConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(sqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    authorIdColumn = SqliteColumn("author_id", JDBCType.INTEGER)
    val authorNameColumn = SqliteColumn("first_name", JDBCType.VARCHAR)
    val authorLastColumn = SqliteColumn("last_name", JDBCType.VARCHAR)

    authorsRow1 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn, 1),
        SqliteColumnValue(authorNameColumn, "Joe1"),
        SqliteColumnValue(authorLastColumn, "LastName1")
      )
    )

    authorsRow2 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn, 2),
        SqliteColumnValue(authorNameColumn, "Joe2"),
        SqliteColumnValue(authorLastColumn, "LastName2")
      )
    )

    authorsRow4 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn, 4),
        SqliteColumnValue(authorNameColumn, "Joe4"),
        SqliteColumnValue(authorLastColumn, "LastName4")
      )
    )

    authorsRow5 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn, 5),
        SqliteColumnValue(authorNameColumn, "Joe5"),
        SqliteColumnValue(authorLastColumn, "LastName5")
      )
    )
  }

  override fun tearDown() {
    try {
      pumpEventsAndWaitForFuture(realDatabaseConnection.close())
      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testSetUp() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(mockResultSet._columns)
    orderVerifier.verify(tableView).showTableRowBatch(mockResultSet.invocations[0])
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testSetUpTableNameIsNull() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, null, mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView).startTableLoading()
  }

  fun testSetUpError() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    val throwable = Throwable()
    `when`(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(throwable))
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView)
      .reportError(eq("Error retrieving rows for table \"tableName\""), refEq(throwable))
    orderVerifier.verify(tableView).stopTableLoading()
  }

  fun testSetUpIsDisposed() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    Disposer.dispose(tableController)
    val future = tableController.setUp()

    // Assert
    pumpEventsAndWaitForFutureException(future)
  }

  fun testSetUpErrorIsDisposed() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    `when`(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(Throwable()))
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act/Assert
    Disposer.dispose(tableController)
    pumpEventsAndWaitForFutureException(tableController.setUp())
  }

  fun testReloadData() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""), edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(mockResultSet._columns)
    orderVerifier.verify(tableView).showTableRowBatch(mockResultSet.invocations[0])
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testReloadDataFailsWhenControllerIsDisposed() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""), edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    Disposer.dispose(tableController)
    val future = tableController.refreshData()

    // Assert
    pumpEventsAndWaitForFutureException(future)
  }

  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnSetup`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(10)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView, times(2)).setFetchNextRowsButtonState(false)
  }

  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnNext`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(2)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      1, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).showTableRowBatch(mockResultSet.invocations[0])
    verify(tableView).showTableRowBatch(mockResultSet.invocations[1])
    verify(tableView, times(3)).setFetchNextRowsButtonState(false)
  }

  fun `test Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test NextBatchOf5`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      5, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 4),
      listOf(5, 9),
      listOf(10, 14)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Next Prev`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(10, 19),
      listOf(0, 9)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Next Prev Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(10, 19),
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(5)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(20, 24)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize At End`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(20)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(11)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(10, 19)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `testChangeBatchSize DisablesPreviousButton`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().rowCountChanged(1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(0, 0)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)

    verify(tableView, times(4)).setFetchPreviousRowsButtonState(false)
  }

  fun `test ChangeBatchSize DisablesNextButton`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().rowCountChanged(100)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(0, 49)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)

    verify(tableView, times(3)).setFetchNextRowsButtonState(false)
  }

  fun `test ChangeBatchSize Max Min`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().rowCountChanged(100)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(1)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(0, 49),
      listOf(0, 0)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)

    verify(tableView, times(4)).setFetchNextRowsButtonState(false)
  }

  fun `test ChangeBatchSize Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(5)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(20, 24),
      listOf(25, 29)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize Prev`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(5)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(20, 24),
      listOf(15, 19)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize Prev ChangeBatchSize Prev Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(2)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(10)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(20, 21),
      listOf(18, 19),
      listOf(18, 27),
      listOf(8, 17),
      listOf(0, 9),
      listOf(10, 19)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test First`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadFirstRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(20, 29),
      listOf(0, 9)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test First ChangeBatchSize`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadFirstRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(5)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(10, 19),
      listOf(0, 9),
      listOf(0, 4)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Last`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(40, 49)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Last LastPage Not Full`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(61)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(60, 60)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Last Prev ChangeBatchSize First`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      10, tableView, "tableName", mockDatabaseConnection, SqliteStatement(""),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged(5)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadFirstRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(40, 49),
      listOf(30, 39),
      listOf(30, 34),
      listOf(0, 4)
    )

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun testSetUpOnRealDb() {
    // Prepare
    tableController = TableController(
      2,
      tableView,
      "tableName",
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author"),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).showTableRowBatch(listOf(authorsRow1, authorsRow2))
  }

  fun testSort() {
    // Prepare
    tableController = TableController(
      2,
      tableView,
      "tableName",
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author"),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).showTableRowBatch(listOf(authorsRow1, authorsRow2))
    orderVerifier.verify(tableView).showTableRowBatch(listOf(authorsRow1, authorsRow2))
    orderVerifier.verify(tableView).showTableRowBatch(listOf(authorsRow5, authorsRow4))
  }

  fun testSortOnSortedQuery() {
    // Prepare
    tableController = TableController(
      2,
      tableView,
      "tableName",
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author ORDER BY author_id DESC"),
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).showTableRowBatch(listOf(authorsRow5, authorsRow4))
    orderVerifier.verify(tableView).showTableRowBatch(listOf(authorsRow1, authorsRow2))
  }

  private fun assertRowSequence(invocations: List<List<SqliteRow>>, expectedInvocations: List<List<Int>>) {
    assertTrue(invocations.size == expectedInvocations.size)
    invocations.forEachIndexed { index, rows ->
      assertEquals(expectedInvocations[index][0], rows.first().values[0].value)
      assertEquals(expectedInvocations[index][1], rows.last().values[0].value)
    }
  }

  private fun getSqliteJdbcService(sqliteFile: VirtualFile, executor: FutureCallbackExecutor): ListenableFuture<DatabaseConnection> {
    return executor.executeAsync {
      val url = "jdbc:sqlite:${sqliteFile.path}"
      val connection = DriverManager.getConnection(url)
      return@executeAsync JdbcDatabaseConnection(connection, sqliteFile, executor)
    }
  }
}
