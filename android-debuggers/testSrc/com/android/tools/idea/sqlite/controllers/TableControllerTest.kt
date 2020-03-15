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
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.mocks.DatabaseConnectionWrapper
import com.android.tools.idea.sqlite.mocks.MockSqliteResultSet
import com.android.tools.idea.sqlite.mocks.MockTableView
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.toSqliteValues
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.google.common.util.concurrent.Futures
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

class TableControllerTest : PlatformTestCase() {
  private lateinit var tableView: MockTableView
  private lateinit var mockResultSet: MockSqliteResultSet
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var tableController: TableController
  private lateinit var mockDatabaseConnection: DatabaseConnection

  private lateinit var orderVerifier: InOrder

  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var realDatabaseConnection: DatabaseConnection
  private var customDatabaseConnection: DatabaseConnection? = null

  private lateinit var authorIdColumn: SqliteColumn
  private lateinit var authorsRow1: SqliteRow
  private lateinit var authorsRow2: SqliteRow
  private lateinit var authorsRow4: SqliteRow
  private lateinit var authorsRow5: SqliteRow

  private val database = FileSqliteDatabase("db", mock(DatabaseConnection::class.java), mock(VirtualFile::class.java))
  private val sqliteTable = SqliteTable("tableName", emptyList(), null, false)

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
      getJdbcDatabaseConnection(sqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    authorIdColumn = SqliteColumn("author_id", SqliteAffinity.INTEGER, false, true)
    val authorNameColumn = SqliteColumn("first_name", SqliteAffinity.TEXT, true, false)
    val authorLastColumn = SqliteColumn("last_name", SqliteAffinity.TEXT, true, false)

    authorsRow1 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(1)),
        SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe1")),
        SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName1"))
      )
    )

    authorsRow2 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(2)),
        SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe2")),
        SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName2"))
      )
    )

    authorsRow4 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(4)),
        SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe4")),
        SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName4"))
      )
    )

    authorsRow5 = SqliteRow(
      listOf(
        SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(5)),
        SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe5")),
        SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName5"))
      )
    )
  }

  override fun tearDown() {
    try {
      pumpEventsAndWaitForFuture(realDatabaseConnection.close())
      if (customDatabaseConnection != null) {
        pumpEventsAndWaitForFuture(customDatabaseConnection!!.close())
      }
      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testSetUp() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project,10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(mockResultSet._columns)
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
    verify(tableView, times(3)).setEditable(true)
  }

  fun testSetUpTableNameIsNull() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { null }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView).startTableLoading()
    verify(tableView, times(3)).setEditable(false)
  }

  fun testRowIdColumnIsNotShownInView() {
    // Prepare
    val sqliteTable = SqliteTable("tableName", emptyList(), RowIdName.ROWID, false)

    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).showTableColumns(mockResultSet._columns.filter { it.name != sqliteTable.rowIdName?.stringName })
  }

  fun testSetUpError() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    val throwable = Throwable()
    `when`(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(throwable))
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).reportError(eq("Error retrieving data from table."), refEq(throwable))
    orderVerifier.verify(tableView).stopTableLoading()
  }

  fun testSetUpIsDisposed() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act/Assert
    Disposer.dispose(tableController)
    pumpEventsAndWaitForFutureException(tableController.setUp())
  }

  fun testRefreshData() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(mockResultSet._columns)
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testReloadDataFailsWhenControllerIsDisposed() {
    // Prepare
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    Disposer.dispose(tableController)
    val future = tableController.refreshData()

    // Assert
    pumpEventsAndWaitForFutureException(future)
  }

  fun testSortByColumnShowsLoadingScreen() {
    // Prepare
    tableController = TableController(
      project,
      2,
      tableView,
      { sqliteTable },
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author"),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    // setup loading
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).stopTableLoading()
    // sort loading
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).stopTableLoading()
  }

  fun testReloadDataAfterSortReturnsSortedData() {
    // Prepare
    tableController = TableController(
      project,
      2,
      tableView,
      { sqliteTable },
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author"),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(emptyList())
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).toCellUpdates())
    orderVerifier.verify(tableView).updateRows(emptyList())
  }

  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnSetup`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(10)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
      project, 1, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    verify(tableView).updateRows(mockResultSet.invocations[1].toCellUpdates())
    verify(tableView, times(3)).setFetchNextRowsButtonState(false)
  }

  fun `test Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Next ShowsLoadingUi`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  fun `test NextBatchOf5`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 5, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Next Prev`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Prev ShowsLoadingUi`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  fun `test Next Prev Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize At End`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(20)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `testChangeBatchSize DisablesPreviousButton`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)

    verify(tableView, times(4)).setFetchPreviousRowsButtonState(false)
  }

  fun `test ChangeBatchSize DisablesNextButton`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)

    verify(tableView, times(3)).setFetchNextRowsButtonState(false)
  }

  fun `test ChangeBatchSize Max Min`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)

    verify(tableView, times(4)).setFetchNextRowsButtonState(false)
  }

  fun `test ChangeBatchSize Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize Prev`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test ChangeBatchSize Prev ChangeBatchSize Prev Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test First`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test First ShowsLoadingUi`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  fun `test First ChangeBatchSize`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Last`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Last ShowsLoadingUi`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  fun `test Last LastPage Not Full`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(61)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test Last Prev ChangeBatchSize First`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(50)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
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
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test InsertAtBeginning Next Prev`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockResultSet.insertRowAtIndex(0, -1)
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(9, 18),
      listOf(-1, 8)
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun `test DeleteAtBeginning Next`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    mockResultSet.deleteRowAtIndex(0)
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(
      listOf(0, 9),
      listOf(11, 20)
    ).map { it.toSqliteValues() }

    assertRowSequence(mockResultSet.invocations, expectedInvocations)
  }

  fun testSetUpOnRealDb() {
    // Prepare
    tableController = TableController(
      project,
      2,
      tableView,
      { sqliteTable },
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author"),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
  }

  fun testSort() {
    // Prepare
    tableController = TableController(
      project,
      2,
      tableView,
      { sqliteTable },
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author"),
      edtExecutor,
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
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(emptyList())
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).toCellUpdates())
  }

  fun testSortOnSortedQuery() {
    // Prepare
    tableController = TableController(
      project,
      2,
      tableView,
      { sqliteTable },
      realDatabaseConnection,
      SqliteStatement("SELECT * FROM author ORDER BY author_id DESC"),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).toCellUpdates())
  }

  fun testUpdateCellUpdatesView() {
    // Prepare
    val customSqliteTable = SqliteTable(
      "tableName",
      listOf(
        SqliteColumn("rowid", SqliteAffinity.INTEGER, false, false),
        SqliteColumn("c1", SqliteAffinity.TEXT, true, false)
      ),
      RowIdName.ROWID,
      false
    )

    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project,
      10,
      tableView,
      { customSqliteTable },
      mockDatabaseConnection,
      SqliteStatement("SELECT * FROM tableName"),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    val targetCol = customSqliteTable.columns[1]
    val newValue = SqliteValue.StringValue("new value")

    val orderVerifier = inOrder(tableView, mockDatabaseConnection)

    // Act
    tableView.listeners.first().updateCellInvoked(1, targetCol, newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(mockDatabaseConnection).execute(
      SqliteStatement(
        "UPDATE tableName SET c1 = ? WHERE rowid = ?",
        listOf("new value", 1).toSqliteValues(),
        "UPDATE tableName SET c1 = 'new value' WHERE rowid = '1'"
      )
    )
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).stopTableLoading()
  }

  fun testUpdateCellOnRealDbIsSuccessfulWith_rowid_() {
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1"))
    testUpdateWorksOnCustomDatabase(customSqliteFile, "tableName", "c1","UPDATE tableName SET c1 = ? WHERE _rowid_ = ?")
  }

  fun testUpdateCellOnRealDbIsSuccessfulWithRowid() {
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1", "_rowid_"))
    testUpdateWorksOnCustomDatabase(customSqliteFile, "tableName", "c1","UPDATE tableName SET c1 = ? WHERE rowid = ?")
  }

  fun testUpdateCellOnRealDbIsSuccessfulWithOid() {
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1", "_rowid_", "rowid"))
    testUpdateWorksOnCustomDatabase(customSqliteFile, "tableName", "c1","UPDATE tableName SET c1 = ? WHERE oid = ?")
  }

  fun testUpdateCellOnRealDbIsSuccessfulWithPrimaryKey() {
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1"), listOf("pk"), true)
    testUpdateWorksOnCustomDatabase(customSqliteFile, "tableName", "c1","UPDATE tableName SET c1 = ? WHERE pk = ?")
  }

  fun testUpdateCellOnRealDbIsSuccessfulWithMultiplePrimaryKeys() {
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1"), listOf("pk1", "pk2"), true)
    testUpdateWorksOnCustomDatabase(customSqliteFile, "tableName",  "c1","UPDATE tableName SET c1 = ? WHERE pk1 = ? AND pk2 = ?")
  }

  fun testEscaping() {
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "table'Name", listOf("c`1"), listOf("p\"k1", "p'k2"))
    testUpdateWorksOnCustomDatabase(
      customSqliteFile, "table'Name", "c`1", "UPDATE `table'Name` SET `c``1` = ? WHERE `p\"k1` = ? AND `p'k2` = ?"
    )
  }

  fun testUpdateCellFailsWhenNoRowIdAndNoPrimaryKey() {
    // Prepare
    val customSqliteFile = sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1", "_rowid_", "rowid", "oid"))
    customDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "tableName" }!!
    val targetCol = targetTable.columns.find { it.name == "c1" }!!

    val originalResultSet = pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(SqliteStatement(selectAllAndRowIdFromTable(targetTable)))
    )
    val targetRow = pumpEventsAndWaitForFuture(originalResultSet!!.getRowBatch(0, 1)).first()

    val originalValue = targetRow.values.first { it.columnName == targetCol.name }.value
    val newValue = SqliteValue.StringValue("test value")

    tableController = TableController(
      project,
      10,
      tableView,
      { targetTable },
      customDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(targetTable)),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    tableView.listeners.first().updateCellInvoked(0, targetCol, newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).reportError("Can't update. No primary keys or rowid column.", null)
    val sqliteResultSet = pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("SELECT * FROM tableName")))
    val rows = pumpEventsAndWaitForFuture(sqliteResultSet?.getRowBatch(0, 1))
    val value = rows.first().values.first { it.columnName == targetCol.name }.value
    assertEquals(originalValue, value)
  }

  fun `test TableWithoutPK AlterTableAddAllRowIdCombinations UpdateCell ReportErrorInView`() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT)",
      insertStatement = "INSERT INTO t1 (c1) VALUES (1)"
    )
    customDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!
    val targetCol = targetTable.columns.find { it.name == "c1" }!!

    val originalResultSet = pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(SqliteStatement(selectAllAndRowIdFromTable(targetTable)))
    )
    val targetRow = pumpEventsAndWaitForFuture(originalResultSet!!.getRowBatch(0, 1)).first()

    val originalValue = targetRow.values.first { it.columnName == targetCol.name }.value
    val newValue = SqliteValue.StringValue("test value")

    val tableProvider = object {
      var table = targetTable
    }

    tableController = TableController(
      project,
      10,
      tableView,
      { tableProvider.table },
      customDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(targetTable)),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("ALTER TABLE t1 ADD COLUMN rowid int")))
    pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("ALTER TABLE t1 ADD COLUMN oid int")))
    pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("ALTER TABLE t1 ADD COLUMN _rowid_ int")))

    val updatedTargetTable = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema()).tables.find { it.name == "t1" }!!
    tableProvider.table = updatedTargetTable

    tableView.listeners.first().updateCellInvoked(0, targetCol, newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).reportError("Can't update. No primary keys or rowid column.", null)
    val sqliteResultSet = pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("SELECT * FROM t1")))
    val rows = pumpEventsAndWaitForFuture(sqliteResultSet?.getRowBatch(0, 1))
    val value = rows.first().values.first { it.columnName == targetCol.name }.value
    assertEquals(originalValue, value)
  }

  fun `test AddRows`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(15)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
  }

  fun `test AddRows RemoveRows`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(15)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[1].take(5).toCellUpdates() + RowDiffOperation.RemoveLastRows(5))
  }

  fun `test AddRows UpdateRows`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(20)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[1].toCellUpdates())
  }

  fun `test AddRows RemoveRows AddRows`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(15)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    tableController = TableController(
      project, 10, tableView, { sqliteTable }, mockDatabaseConnection, SqliteStatement(""), edtExecutor, edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(mockResultSet.invocations[1].take(5).toCellUpdates() + RowDiffOperation.RemoveLastRows(5))
    orderVerifier.verify(tableView).updateRows(
      mockResultSet.invocations[2].take(5).toCellUpdates() + mockResultSet.invocations[2].drop(5).map { RowDiffOperation.AddRow(it) }
    )
  }

  fun `test ShowTable DropTable RefreshShowsEmptyTable`() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT)",
      insertStatement = "INSERT INTO t1 (c1) VALUES (1)"
    )
    customDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!

    val tableProvider = object {
      var table: SqliteTable? = targetTable
    }

    tableController = TableController(
      project,
      10,
      tableView,
      { tableProvider.table },
      customDatabaseConnection!!,
      SqliteStatement("SELECT * FROM ${targetTable.name}"),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("DROP TABLE t1")))
    tableProvider.table = null

    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).showTableColumns(targetTable.columns)
    orderVerifier.verify(tableView).updateRows(listOf(
      RowDiffOperation.AddRow (SqliteRow(listOf(SqliteColumnValue("c1", SqliteValue.fromAny("1")))))
    ))

    orderVerifier.verify(tableView).resetView()
    orderVerifier.verify(tableView).reportError(eq("Error retrieving data from table."), any(Throwable::class.java))
  }

  fun `test ShowTable DropTable EditTableShowsError`() {
    // Prepare
    val customSqliteFile = sqliteUtil.createAdHocSqliteDatabase(
      createStatement = "CREATE TABLE t1 (c1 INT)",
      insertStatement = "INSERT INTO t1 (c1) VALUES (1)"
    )
    customDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(customSqliteFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!
    val targetCol = targetTable.columns.find { it.name == "c1" }!!

    val tableProvider = object {
      var table: SqliteTable? = targetTable
    }

    tableController = TableController(
      project,
      10,
      tableView,
      { tableProvider.table },
      customDatabaseConnection!!,
      SqliteStatement(selectAllAndRowIdFromTable(targetTable)),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(SqliteStatement("DROP TABLE t1")))
    tableProvider.table = null

    tableView.listeners.first().updateCellInvoked(0, targetCol, SqliteValue.StringValue("test value"))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).reportError("Can't update. Table not found.", null)
    orderVerifier.verifyNoMoreInteractions()
  }

  private fun testUpdateWorksOnCustomDatabase(databaseFile: VirtualFile, targetTableName: String, targetColumnName: String, expectedSqliteStatement: String) {
    // Prepare
    customDatabaseConnection = pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(databaseFile, FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()))
    )

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == targetTableName }!!
    val targetCol = targetTable.columns.find { it.name == targetColumnName }!!

    val originalResultSet = pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(SqliteStatement(selectAllAndRowIdFromTable(targetTable)))
    )
    val targetRow = pumpEventsAndWaitForFuture(originalResultSet!!.getRowBatch(0, 1)).first()

    val newValue = SqliteValue.StringValue("test value")

    val databaseConnectionWrapper = DatabaseConnectionWrapper(customDatabaseConnection!!)

    tableController = TableController(
      project,
      10,
      tableView,
      { targetTable },
      databaseConnectionWrapper,
      SqliteStatement(selectAllAndRowIdFromTable(targetTable)),
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    tableView.listeners.first().updateCellInvoked(0, targetCol, newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val sqliteResultSet = pumpEventsAndWaitForFuture(customDatabaseConnection!!.execute(
      SqliteStatement("SELECT * FROM ${AndroidSqlLexer.getValidName(targetTableName)}")
    ))
    val rows = pumpEventsAndWaitForFuture(sqliteResultSet?.getRowBatch(0, 1))
    val value = rows.first().values.first { it.columnName == targetCol.name }.value
    assertEquals(SqliteValue.StringValue("test value"), value)
    val executedUpdateStatement = databaseConnectionWrapper.executedSqliteStatements.first { it.startsWith("UPDATE") }
    assertEquals(expectedSqliteStatement, executedUpdateStatement)
  }

  private fun assertRowSequence(invocations: List<List<SqliteRow>>, expectedInvocations: List<List<SqliteValue>>) {
    assertTrue(invocations.size == expectedInvocations.size)
    invocations.forEachIndexed { index, rows ->
      assertEquals(expectedInvocations[index][0], rows.first().values[0].value)
      assertEquals(expectedInvocations[index][1], rows.last().values[0].value)
    }
  }

  private fun List<SqliteRow>.toCellUpdates(): List<RowDiffOperation.UpdateCell> {
    val result = mutableListOf<RowDiffOperation.UpdateCell>()

    for (rowIndex in indices) {
      result.addAll(get(rowIndex).values.mapIndexed { colIndex, value -> RowDiffOperation.UpdateCell(value, rowIndex, colIndex) })
    }

    return result
  }
}
