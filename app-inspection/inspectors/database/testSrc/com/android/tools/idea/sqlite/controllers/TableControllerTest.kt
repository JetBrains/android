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

import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureCancellation
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.mocks.DatabaseConnectionWrapper
import com.android.tools.idea.sqlite.mocks.FakeSqliteResultSet
import com.android.tools.idea.sqlite.mocks.FakeTableView
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportQueryResultsDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportTableDialogParams
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteQueryResult
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.ui.tableView.OrderBy
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.android.tools.idea.sqlite.ui.tableView.ViewColumn
import com.android.tools.idea.sqlite.utils.SqliteTestUtil
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.android.tools.idea.sqlite.utils.toViewColumn
import com.android.tools.idea.sqlite.utils.toViewColumns
import com.android.tools.idea.testing.runDispatching
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.refEq
import org.mockito.kotlin.whenever

private val authorIdColumn =
  ResultSetSqliteColumn(
    "author_id",
    SqliteAffinity.INTEGER,
    isNullable = false,
    inPrimaryKey = true,
  )
private val authorNameColumn =
  ResultSetSqliteColumn("first_name", SqliteAffinity.TEXT, isNullable = true, inPrimaryKey = false)
private val authorLastColumn =
  SqliteColumn("last_name", SqliteAffinity.TEXT, isNullable = true, inPrimaryKey = false)
private val authorsRow1 =
  SqliteRow(
    listOf(
      SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(1)),
      SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe1")),
      SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName1")),
    )
  )
private val authorsRow2 =
  SqliteRow(
    listOf(
      SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(2)),
      SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe2")),
      SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName2")),
    )
  )

private val authorsRow4 =
  SqliteRow(
    listOf(
      SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(4)),
      SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe4")),
      SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName4")),
    )
  )

private val authorsRow5 =
  SqliteRow(
    listOf(
      SqliteColumnValue(authorIdColumn.name, SqliteValue.fromAny(5)),
      SqliteColumnValue(authorNameColumn.name, SqliteValue.fromAny("Joe5")),
      SqliteColumnValue(authorLastColumn.name, SqliteValue.fromAny("LastName5")),
    )
  )

@RunWith(JUnit4::class)
@RunsInEdt
class TableControllerTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(projectRule, disposableRule, EdtRule())

  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  private val tableView = spy(FakeTableView::class.java)
  private val sqliteResultSet = FakeSqliteResultSet()
  private val edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())

  private val orderVerifier = inOrder(tableView)

  private val sqliteUtil =
    SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())

  private val realDatabaseConnection by lazy {
    pumpEventsAndWaitForFuture(
      getJdbcDatabaseConnection(
        disposable,
        sqliteUtil.createTestSqliteDatabase(),
        FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
      )
    )
  }
  private val mockDatabaseConnection = mock(DatabaseConnection::class.java)
  private var customDatabaseConnection: DatabaseConnection? = null
  private val databaseRepository by lazy { OpenDatabaseRepository(project, edtExecutor) }

  private val fileDatabaseId =
    SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("file")))
  private val realDatabaseConnectionId = SqliteDatabaseId.fromLiveDatabase("real", 0)
  private val mockDatabaseConnectionId = SqliteDatabaseId.fromLiveDatabase("mock", 1)

  private val sqliteTable = SqliteTable("tableName", emptyList(), null, false)

  @Before
  fun setUp() {
    sqliteUtil.setUp()
    runDispatching(AndroidDispatchers.uiThread) {
      databaseRepository.addDatabaseConnection(realDatabaseConnectionId, realDatabaseConnection)
      databaseRepository.addDatabaseConnection(mockDatabaseConnectionId, mockDatabaseConnection)
      databaseRepository.addDatabaseConnection(fileDatabaseId, mockDatabaseConnection)
    }
  }

  @After
  fun tearDown() {
    pumpEventsAndWaitForFuture(realDatabaseConnection.close())
    if (customDatabaseConnection != null) {
      pumpEventsAndWaitForFuture(customDatabaseConnection!!.close())
    }
    sqliteUtil.tearDown()
  }

  @Test
  fun testSetUp() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        rowBatchSize = 10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
        liveUpdatesEnabled = false,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(sqliteResultSet._columns.toViewColumns())
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(), any())
    verify(tableView, times(3)).setEditable(true)
  }

  @Test
  fun testSetUpTableNameIsNull() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { null },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView).startTableLoading()
    verify(tableView, times(3)).setEditable(false)
  }

  @Test
  fun testRowIdColumnIsNotShownInView() {
    // Prepare
    val sqliteTable = SqliteTable("tableName", emptyList(), RowIdName.ROWID, false)

    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier
      .verify(tableView)
      .showTableColumns(
        sqliteResultSet._columns
          .filter { it.name != sqliteTable.rowIdName?.stringName }
          .toViewColumns()
      )
  }

  @Test
  fun testSetUpError() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    val throwable = Throwable()
    whenever(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(throwable))
    whenever(mockDatabaseConnection.query(any())).thenReturn(Futures.immediateFuture(mockResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    val error = pumpEventsAndWaitForFutureException(tableController.setUp())

    // Assert
    assertEquals(error.cause, throwable)
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier
      .verify(tableView)
      .reportError(eq("Error retrieving data from table."), refEq(throwable))
    orderVerifier.verify(tableView).stopTableLoading()
  }

  @Test
  fun testSetUpIsDisposed() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    Disposer.dispose(tableController)
    val future = tableController.setUp()

    // Assert
    pumpEventsAndWaitForFutureCancellation(future)
  }

  @Test
  fun testSetUpErrorIsDisposed() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    whenever(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(Throwable()))
    whenever(mockDatabaseConnection.query(any())).thenReturn(Futures.immediateFuture(mockResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act/Assert
    Disposer.dispose(tableController)
    pumpEventsAndWaitForFutureCancellation(tableController.setUp())
  }

  @Test
  fun testRefreshData() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(sqliteResultSet._columns.toViewColumns())
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(), any())
  }

  @Test
  fun testRefreshDataScheduledOneAtATime() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    val future1 = tableController.refreshData()
    val future2 = tableController.refreshData()
    pumpEventsAndWaitForFuture(future2)
    val future3 = tableController.refreshData()

    // Assert
    assertEquals(future1, future2)
    assertTrue(future2 != future3)
  }

  @Test
  fun testReloadDataFailsWhenControllerIsDisposed() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    Disposer.dispose(tableController)
    val future = tableController.refreshData()

    // Assert
    pumpEventsAndWaitForFutureCancellation(future)
  }

  @Test
  fun testSortByColumnShowsLoadingScreen() {
    // Prepare
    val tableController =
      TableController(
        project,
        2,
        tableView,
        realDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM author"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    // setup loading
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).stopTableLoading()
    // sort loading
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).stopTableLoading()
  }

  @Test
  fun testReloadDataAfterSortReturnsSortedData() {
    // Prepare
    val tableController =
      TableController(
        project,
        2,
        tableView,
        realDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM author"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier
      .verify(tableView)
      .updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).toCellUpdates())
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).toCellUpdates())
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier.verify(tableView).updateRows(emptyList())
  }

  @Test
  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnSetup`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(10)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView, times(2)).setFetchNextRowsButtonState(false)
  }

  @Test
  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnNext`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(2)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        1,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView).updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    verify(tableView).updateRows(sqliteResultSet.invocations[1].toCellUpdates())
    verify(tableView, times(3)).setFetchNextRowsButtonState(false)
  }

  @Test
  fun `test Next`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(20, 29)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)

    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier.verify(tableView).setRowOffset(10)
    orderVerifier.verify(tableView).setRowOffset(20)
  }

  @Test
  fun `test Next ShowsLoadingUi`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  @Test
  fun `test NextBatchOf5`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        5,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 4), listOf(5, 9), listOf(10, 14)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)

    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier.verify(tableView).setRowOffset(5)
    orderVerifier.verify(tableView).setRowOffset(10)
  }

  @Test
  fun `test Next Prev`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

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
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(20, 29), listOf(10, 19), listOf(0, 9)).map {
        it.toSqliteValues()
      }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)

    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier.verify(tableView).setRowOffset(10)
    orderVerifier.verify(tableView).setRowOffset(20)
    orderVerifier.verify(tableView).setRowOffset(10)
    orderVerifier.verify(tableView).setRowOffset(0)
  }

  @Test
  fun `test Prev ShowsLoadingUi`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  @Test
  fun `test Next Prev Next`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

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
    val expectedInvocations =
      listOf(
          listOf(0, 9),
          listOf(10, 19),
          listOf(20, 29),
          listOf(10, 19),
          listOf(0, 9),
          listOf(10, 19),
          listOf(20, 29),
        )
        .map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test ChangeBatchSize`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("5")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(20, 29), listOf(20, 24)).map {
        it.toSqliteValues()
      }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test ChangeBatchSize At End`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(20)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("11")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(10, 19)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `testChangeBatchSize DisablesPreviousButton`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().rowCountChanged("1")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(listOf(0, 9), listOf(0, 0)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)

    verify(tableView, times(4)).setFetchPreviousRowsButtonState(false)
  }

  @Test
  fun `test ChangeBatchSize DisablesNextButton`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(50)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().rowCountChanged("100")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(listOf(0, 9), listOf(0, 49)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)

    verify(tableView, times(3)).setFetchNextRowsButtonState(false)
  }

  @Test
  fun `test ChangeBatchSize Max Min`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(50)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().rowCountChanged("100")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("1")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(0, 49), listOf(0, 0)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)

    verify(tableView, times(4)).setFetchNextRowsButtonState(false)
  }

  @Test
  fun `test ChangeBatchSize Next`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("5")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(20, 29), listOf(20, 24), listOf(25, 29)).map {
        it.toSqliteValues()
      }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test ChangeBatchSize Prev`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("5")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(20, 29), listOf(20, 24), listOf(15, 19)).map {
        it.toSqliteValues()
      }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test ChangeBatchSize Prev ChangeBatchSize Prev Next`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("2")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("10")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(
          listOf(0, 9),
          listOf(10, 19),
          listOf(20, 29),
          listOf(20, 21),
          listOf(18, 19),
          listOf(18, 27),
          listOf(8, 17),
          listOf(0, 9),
          listOf(10, 19),
        )
        .map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test First`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadFirstRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(20, 29), listOf(0, 9)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test First ShowsLoadingUi`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  @Test
  fun `test First ChangeBatchSize`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadFirstRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("5")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(10, 19), listOf(0, 9), listOf(0, 4)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test Last`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(50)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(listOf(0, 9), listOf(40, 49)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test Last ShowsLoadingUi`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(50)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).startTableLoading()
    verify(tableView, times(2)).stopTableLoading()
  }

  @Test
  fun `test Last LastPage Not Full`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(61)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(listOf(0, 9), listOf(60, 60)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test Last Prev ChangeBatchSize First`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(50)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadLastRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().rowCountChanged("5")
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadFirstRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(40, 49), listOf(30, 39), listOf(30, 34), listOf(0, 4)).map {
        it.toSqliteValues()
      }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test InsertAtBeginning Next Prev`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteResultSet.insertRowAtIndex(0, -1)
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations =
      listOf(listOf(0, 9), listOf(9, 18), listOf(-1, 8)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun `test DeleteAtBeginning Next`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    sqliteResultSet.deleteRowAtIndex(0)
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val expectedInvocations = listOf(listOf(0, 9), listOf(11, 20)).map { it.toSqliteValues() }

    assertRowSequence(sqliteResultSet.invocations, expectedInvocations)
  }

  @Test
  fun testSetUpOnRealDb() {
    // Prepare
    val tableController =
      TableController(
        project,
        2,
        tableView,
        realDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM author"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
  }

  @Test
  fun testSort() {
    // Prepare
    val tableController =
      TableController(
        project,
        2,
        tableView,
        realDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM author"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).setColumnSortIndicator(OrderBy.Desc(authorIdColumn.name))
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).toCellUpdates())
    orderVerifier.verify(tableView).setColumnSortIndicator(OrderBy.Asc(authorIdColumn.name))
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).toCellUpdates())
    orderVerifier.verify(tableView).setColumnSortIndicator(OrderBy.NotOrdered)
    orderVerifier.verify(tableView).updateRows(emptyList())
  }

  @Test
  fun testSortOnNewColumn() {
    // Prepare
    val tableController =
      TableController(
        project,
        2,
        tableView,
        realDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM author"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorNameColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(listOf(authorsRow1, authorsRow2).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).setColumnSortIndicator(OrderBy.Desc(authorIdColumn.name))
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).toCellUpdates())
    orderVerifier.verify(tableView).setColumnSortIndicator(OrderBy.Desc(authorNameColumn.name))
    orderVerifier.verify(tableView).updateRows(emptyList())
  }

  @Test
  fun testSortOnSortedQuery() {
    // Prepare
    val tableController =
      TableController(
        project,
        2,
        tableView,
        realDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM author ORDER BY author_id DESC"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().toggleOrderByColumnInvoked(authorIdColumn.toViewColumn())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(listOf(authorsRow5, authorsRow4).map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(emptyList())
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow1, authorsRow2).toCellUpdates())
    orderVerifier.verify(tableView).updateRows(listOf(authorsRow5, authorsRow4).toCellUpdates())
  }

  @Test
  fun testUpdateCellUpdatesView() {
    // Prepare
    val customSqliteTable =
      SqliteTable(
        "tableName",
        listOf(
          SqliteColumn("rowid", SqliteAffinity.INTEGER, isNullable = false, inPrimaryKey = false),
          SqliteColumn("c1", SqliteAffinity.TEXT, true, inPrimaryKey = false),
        ),
        RowIdName.ROWID,
        false,
      )

    val resultSetCols =
      listOf(
        ResultSetSqliteColumn(
          "rowid",
          SqliteAffinity.INTEGER,
          isNullable = false,
          inPrimaryKey = false,
        ),
        ResultSetSqliteColumn("c1", SqliteAffinity.TEXT, isNullable = true, inPrimaryKey = false),
      )

    whenever(mockDatabaseConnection.execute(any())).thenReturn(Futures.immediateFuture(Unit))
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { customSqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tableName"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    val targetCol = resultSetCols[1]
    val newValue = SqliteValue.StringValue("new value")

    val orderVerifier = inOrder(tableView, mockDatabaseConnection)

    // Act
    tableView.listeners.first().updateCellInvoked(1, targetCol.toViewColumn(), newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier
      .verify(mockDatabaseConnection)
      .execute(
        SqliteStatement(
          SqliteStatementType.UPDATE,
          "UPDATE tableName SET c1 = ? WHERE rowid = ?",
          listOf("new value", 1).toSqliteValues(),
          "UPDATE tableName SET c1 = 'new value' WHERE rowid = '1'",
        )
      )
    orderVerifier.verify(tableView).stopTableLoading()
  }

  @Test
  fun testUpdateCellOnRealDbIsSuccessfulWith_rowid_() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1"))
    testUpdateWorksOnCustomDatabase(
      customSqliteFile,
      "tableName",
      "c1",
      "UPDATE tableName SET c1 = ? WHERE _rowid_ = ?",
    )
  }

  @Test
  fun testUpdateCellOnRealDbIsSuccessfulWithRowid() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1", "_rowid_"))
    testUpdateWorksOnCustomDatabase(
      customSqliteFile,
      "tableName",
      "c1",
      "UPDATE tableName SET c1 = ? WHERE rowid = ?",
    )
  }

  @Test
  fun testUpdateCellOnRealDbIsSuccessfulWithOid() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1", "_rowid_", "rowid"))
    testUpdateWorksOnCustomDatabase(
      customSqliteFile,
      "tableName",
      "c1",
      "UPDATE tableName SET c1 = ? WHERE oid = ?",
    )
  }

  @Test
  fun testUpdateCellOnRealDbIsSuccessfulWithPrimaryKey() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1"), listOf("pk"), true)
    testUpdateWorksOnCustomDatabase(
      customSqliteFile,
      "tableName",
      "c1",
      "UPDATE tableName SET c1 = ? WHERE pk = ?",
    )
  }

  @Test
  fun testUpdateCellOnRealDbIsSuccessfulWithMultiplePrimaryKeys() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "customDb",
        "tableName",
        listOf("c1"),
        listOf("pk1", "pk2"),
        true,
      )
    testUpdateWorksOnCustomDatabase(
      customSqliteFile,
      "tableName",
      "c1",
      "UPDATE tableName SET c1 = ? WHERE pk1 = ? AND pk2 = ?",
    )
  }

  @Test
  fun testEscaping() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "customDb",
        "table'Name",
        listOf("c`1"),
        listOf("p\"k1", "p'k2"),
      )
    testUpdateWorksOnCustomDatabase(
      customSqliteFile,
      "table'Name",
      "c`1",
      "UPDATE `table'Name` SET `c``1` = ? WHERE `p\"k1` = ? AND `p'k2` = ?",
    )
  }

  @Test
  fun testUpdateCellFailsWhenNoRowIdAndNoPrimaryKey() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "customDb",
        "tableName",
        listOf("c1", "_rowid_", "rowid", "oid"),
      )
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, customDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "tableName" }!!
    val targetCol = targetTable.columns.find { it.name == "c1" }!!

    val originalResultSet =
      pumpEventsAndWaitForFuture(
        customDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(targetTable))
        )
      )
    val targetRow = pumpEventsAndWaitForFuture(originalResultSet.getRowBatch(0, 1)).rows.first()

    val originalValue = targetRow.values.first { it.columnName == targetCol.name }.value
    val newValue = SqliteValue.StringValue("test value")

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { targetTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(targetTable)),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    tableView.listeners
      .first()
      .updateCellInvoked(0, targetCol.toResultSetCol().toViewColumn(), newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertEquals("Can't execute update: ", tableView.errorReported.first().first)
    assertEquals("No primary keys or rowid column", tableView.errorReported.first().second?.message)

    orderVerifier.verify(tableView).stopTableLoading()
    orderVerifier.verify(tableView).revertLastTableCellEdit()
    orderVerifier.verify(tableView).stopTableLoading()

    val sqliteResultSet =
      pumpEventsAndWaitForFuture(
        customDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tableName")
        )
      )
    val rows = pumpEventsAndWaitForFuture(sqliteResultSet.getRowBatch(0, 1)).rows
    val value = rows.first().values.first { it.columnName == targetCol.name }.value
    assertEquals(originalValue, value)
  }

  @Test
  fun `test TableWithoutPK AlterTableAddAllRowIdCombinations UpdateCell ReportErrorInView`() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (1)",
      )
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, customDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!
    val targetCol = targetTable.columns.find { it.name == "c1" }!!

    val originalResultSet =
      pumpEventsAndWaitForFuture(
        customDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(targetTable))
        )
      )
    val targetRow = pumpEventsAndWaitForFuture(originalResultSet.getRowBatch(0, 1)).rows.first()

    val originalValue = targetRow.values.first { it.columnName == targetCol.name }.value
    val newValue = SqliteValue.StringValue("test value")

    val tableProvider =
      object {
        var table = targetTable
      }

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { tableProvider.table },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(targetTable)),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD COLUMN rowid int")
      )
    )
    pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD COLUMN oid int")
      )
    )
    pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD COLUMN _rowid_ int")
      )
    )

    val updatedTargetTable =
      pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema()).tables.find {
        it.name == "t1"
      }!!
    tableProvider.table = updatedTargetTable

    tableView.listeners
      .first()
      .updateCellInvoked(0, targetCol.toResultSetCol().toViewColumn(), newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    assertEquals("Can't execute update: ", tableView.errorReported.first().first)
    assertEquals("No primary keys or rowid column", tableView.errorReported.first().second?.message)
    val sqliteResultSet =
      pumpEventsAndWaitForFuture(
        customDatabaseConnection!!.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
        )
      )
    val rows = pumpEventsAndWaitForFuture(sqliteResultSet.getRowBatch(0, 1)).rows
    val value = rows.first().values.first { it.columnName == targetCol.name }.value
    assertEquals(originalValue, value)
  }

  @Test
  fun `test AddRows`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(15)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
  }

  @Test
  fun `test AddRows RemoveRows`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(15)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier
      .verify(tableView)
      .updateRows(
        sqliteResultSet.invocations[1].take(5).toCellUpdates() + RowDiffOperation.RemoveLastRows(5)
      )
  }

  @Test
  fun `test AddRows UpdateRows`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(20)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).updateRows(sqliteResultSet.invocations[1].toCellUpdates())
  }

  @Test
  fun `test AddRows RemoveRows AddRows`() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(15)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())
    tableView.listeners.first().loadNextRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    tableView.listeners.first().loadPreviousRowsInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier
      .verify(tableView)
      .updateRows(
        sqliteResultSet.invocations[1].take(5).toCellUpdates() + RowDiffOperation.RemoveLastRows(5)
      )
    orderVerifier
      .verify(tableView)
      .updateRows(
        sqliteResultSet.invocations[2].take(5).toCellUpdates() +
          sqliteResultSet.invocations[2].drop(5).map { RowDiffOperation.AddRow(it) }
      )
  }

  @Test
  fun `test ShowTable DropTable RefreshShowsEmptyTable`() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (1)",
      )
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, customDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!

    val tableProvider =
      object {
        var table: SqliteTable? = targetTable
      }

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { tableProvider.table },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM ${targetTable.name}"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "DROP TABLE t1")
      )
    )
    tableProvider.table = null

    pumpEventsAndWaitForFutureException(tableController.refreshData())

    // Assert
    orderVerifier.verify(tableView).showTableColumns(targetTable.columns.toViewColumns())
    orderVerifier
      .verify(tableView)
      .updateRows(
        listOf(
          RowDiffOperation.AddRow(
            SqliteRow(listOf(SqliteColumnValue("c1", SqliteValue.fromAny("1"))))
          )
        )
      )

    orderVerifier.verify(tableView).resetView()
    orderVerifier.verify(tableView).reportError(eq("Error retrieving data from table."), any())
  }

  @Test
  fun testRowsDiffWorksWhenColumnsChange() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (42)",
      )
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, customDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!

    val tableProvider =
      object {
        var table: SqliteTable? = targetTable
      }

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { tableProvider.table },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM ${targetTable.name}"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD COLUMN c2 text")
      )
    )
    pumpEventsAndWaitForFuture(tableController.refreshData())

    // Assert
    val targetTableAfterAlterTable =
      pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema()).tables.find {
        it.name == "t1"
      }!!

    orderVerifier.verify(tableView).showTableColumns(targetTable.columns.toViewColumns())
    orderVerifier
      .verify(tableView)
      .updateRows(
        listOf(
          RowDiffOperation.AddRow(
            SqliteRow(listOf(SqliteColumnValue("c1", SqliteValue.StringValue("42"))))
          )
        )
      )

    orderVerifier
      .verify(tableView)
      .showTableColumns(targetTableAfterAlterTable.columns.toViewColumns())
    orderVerifier
      .verify(tableView)
      .updateRows(
        listOf(
          RowDiffOperation.AddRow(
            SqliteRow(
              listOf(
                SqliteColumnValue("c1", SqliteValue.StringValue("42")),
                SqliteColumnValue("c2", SqliteValue.NullValue),
              )
            )
          )
        )
      )
  }

  @Test
  fun `test ShowTable DropTable EditTableShowsError`() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (1)",
      )
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, customDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == "t1" }!!
    val targetCol = targetTable.columns.find { it.name == "c1" }!!

    val tableProvider =
      object {
        var table: SqliteTable? = targetTable
      }

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { tableProvider.table },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(targetTable)),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    pumpEventsAndWaitForFuture(
      customDatabaseConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "DROP TABLE t1")
      )
    )
    tableProvider.table = null

    tableView.listeners
      .first()
      .updateCellInvoked(
        0,
        targetCol.toResultSetCol().toViewColumn(),
        SqliteValue.StringValue("test value"),
      )
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).reportError("Can't update. Table not found.", null)
    orderVerifier.verifyNoMoreInteractions()
  }

  @Test
  fun testDisposeCancelsExecution() {
    val executionFuture = SettableFuture.create<SqliteResultSet>()
    whenever(mockDatabaseConnection.query(any())).thenReturn(executionFuture)
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { null },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )

    Disposer.register(disposable, tableController)

    // Act
    tableController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Disposer.dispose(tableController)
    // Assert
    pumpEventsAndWaitForFutureCancellation(executionFuture)
  }

  @Test
  fun testCancelRunningStatementAnalytics() {
    // Prepare
    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(
      DatabaseInspectorAnalyticsTracker::class.java,
      mockTrackerService,
    )

    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { null },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    tableController.setUp()
    Disposer.register(disposable, tableController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    tableView.listeners.first().cancelRunningStatementInvoked()

    // Assert
    verify(mockTrackerService)
      .trackStatementExecutionCanceled(
        AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_ONLINE,
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.UNKNOWN_STATEMENT_CONTEXT,
      )
  }

  @Test
  fun testRefreshDataAnalytics() {
    // Prepare
    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(
      DatabaseInspectorAnalyticsTracker::class.java,
      mockTrackerService,
    )

    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { null },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    tableController.setUp()
    Disposer.register(disposable, tableController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    tableView.listeners.first().refreshDataInvoked()

    // Assert
    verify(mockTrackerService)
      .trackTargetRefreshed(AppInspectionEvent.DatabaseInspectorEvent.TargetType.TABLE_TARGET)
  }

  @Test
  fun testShowExportToFileDialogInvoked_table() {
    val table = SqliteTable("tableName", mock(), null, false)
    val expectedDialogParams =
      ExportTableDialogParams(
        mockDatabaseConnectionId,
        table.name,
        Origin.TABLE_CONTENTS_EXPORT_BUTTON,
      )
    testShowExportToFileDialogInvoked({ table }, mock(), expectedDialogParams)
  }

  @Test
  fun testShowExportToFileDialogInvoked_query() {
    val sqliteStatement = SqliteStatement(SqliteStatementType.SELECT, "select * from table1337")
    val expectedDialogParams =
      ExportQueryResultsDialogParams(
        mockDatabaseConnectionId,
        sqliteStatement,
        Origin.QUERY_RESULTS_EXPORT_BUTTON,
      )
    testShowExportToFileDialogInvoked({ null }, sqliteStatement, expectedDialogParams)
  }

  private fun testShowExportToFileDialogInvoked(
    tableSupplier: () -> SqliteTable?,
    sqliteStatement: SqliteStatement,
    expectedDialogParams: ExportDialogParams,
  ) {
    // Prepare
    val showExportDialog: (ExportDialogParams) -> Unit = mock()

    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        tableSupplier,
        databaseRepository,
        sqliteStatement,
        {},
        showExportDialog,
        edtExecutor,
        edtExecutor,
      )
    tableController.setUp()
    Disposer.register(disposable, tableController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    tableView.listeners.first().showExportToFileDialogInvoked()

    // Assert
    verify(showExportDialog).invoke(expectedDialogParams)
  }

  @Test
  fun testEditCellAnalytics() {
    // Prepare
    val customSqliteTable =
      SqliteTable(
        "tableName",
        listOf(
          SqliteColumn("rowid", SqliteAffinity.INTEGER, isNullable = false, inPrimaryKey = false)
        ),
        RowIdName.ROWID,
        false,
      )

    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(
      DatabaseInspectorAnalyticsTracker::class.java,
      mockTrackerService,
    )

    whenever(mockDatabaseConnection.execute(any())).thenReturn(Futures.immediateFuture(Unit))
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { customSqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM tableName"),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    val targetCol = customSqliteTable.columns[0]
    val newValue = SqliteValue.StringValue("new value")

    // Act
    tableView.listeners
      .first()
      .updateCellInvoked(1, targetCol.toResultSetCol().toViewColumn(), newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockTrackerService).trackTableCellEdited()
  }

  @Test
  fun testToggleLiveUpdatesAnalytics() {
    // Prepare
    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(
      DatabaseInspectorAnalyticsTracker::class.java,
      mockTrackerService,
    )

    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { null },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    tableController.setUp()
    Disposer.register(disposable, tableController)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    tableView.listeners.first().toggleLiveUpdatesInvoked()
    tableView.listeners.first().toggleLiveUpdatesInvoked()

    // Assert
    verify(mockTrackerService).trackLiveUpdatedToggled(true)
    verify(mockTrackerService).trackLiveUpdatedToggled(false)
  }

  @Test
  fun testNotifyDataMightBeStaleUpdatesTable() {
    // Prepare
    val mockResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any())).thenReturn(Futures.immediateFuture(mockResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    tableView.listeners.first().toggleLiveUpdatesInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    // 1st invocation by setUp, 2nd by toggleLiveUpdatesInvoked
    verify(tableView, times(2)).showTableColumns(mockResultSet._columns.toViewColumns())
    // invocation by setUp
    verify(tableView, times(1))
      .updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    // invocation by toggleLiveUpdatesInvoked
    verify(tableView, times(1)).updateRows(emptyList())
    // invocation by setUp
    verify(tableView, times(1)).startTableLoading()
  }

  @Test
  fun testToggleLiveUpdatesKeepsTableNotEditable() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet()
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { null },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView, times(3)).setEditable(false)

    // Act
    tableView.listeners.first().toggleLiveUpdatesInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(6)).setEditable(false)

    tableView.listeners.first().toggleLiveUpdatesInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(7)).setEditable(false)
    verify(tableView, times(0)).setEditable(true)
  }

  @Test
  fun testViewsAreNotEditable() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT); CREATE VIEW my_view AS SELECT * FROM t1",
        insertStatement = "INSERT INTO t1 (c1) VALUES (1)",
      )
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(customSqliteFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, customDatabaseConnection!!)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val myView = schema.tables.find { it.name == "my_view" }!!

    val tableProvider =
      object {
        var table: SqliteTable? = myView
      }

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { tableProvider.table },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(myView)),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    verify(tableView, times(3)).setEditable(false)
    verify(tableView, times(0)).setEditable(true)
  }

  @Test
  fun testSetUpFailsWithAppInspectionException() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    val connectionException = AppInspectionConnectionException("Connection closed")
    whenever(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(connectionException))
    whenever(mockDatabaseConnection.query(any())).thenReturn(Futures.immediateFuture(mockResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    val error = pumpEventsAndWaitForFutureException(tableController.setUp())

    // Assert
    assertEquals(error.cause, connectionException)
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).resetView()
    orderVerifier.verify(tableView).stopTableLoading()
  }

  @Test
  fun testColumnInformationFromSchema() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    whenever(mockResultSet.getRowBatch(any(), any()))
      .thenReturn(Futures.immediateFuture(SqliteQueryResult(emptyList())))
    whenever(mockResultSet.totalRowCount).thenReturn(Futures.immediateFuture(0))
    whenever(mockResultSet.columns)
      .thenReturn(Futures.immediateFuture(listOf(ResultSetSqliteColumn("c1", null, null, null))))

    whenever(mockDatabaseConnection.query(any())).thenReturn(Futures.immediateFuture(mockResultSet))

    val table =
      SqliteTable(
        "t1",
        listOf(SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = true)),
        null,
        false,
      )

    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { table },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier
      .verify(tableView)
      .showTableColumns(listOf(ViewColumn("c1", inPrimaryKey = true, isNullable = false)))
  }

  @Test
  fun testLiveUpdatesDisabledAndReadOnlyForFileDatabase() {
    // Prepare
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        fileDatabaseId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)

    // Act
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Assert
    orderVerifier.verify(tableView).setLiveUpdatesButtonState(false)
    orderVerifier.verify(tableView).setRefreshButtonState(false)
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(sqliteResultSet._columns.toViewColumns())
    orderVerifier.verify(tableView).setRowOffset(0)
    orderVerifier
      .verify(tableView)
      .updateRows(sqliteResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(tableView).stopTableLoading()
    verify(tableView, times(3)).setEditable(false)
  }

  @Test
  fun testRowCountInputValidation() {
    // Prepare
    val sqliteResultSet = FakeSqliteResultSet(50)
    whenever(mockDatabaseConnection.query(any()))
      .thenReturn(Futures.immediateFuture(sqliteResultSet))
    val tableController =
      TableController(
        project,
        10,
        tableView,
        mockDatabaseConnectionId,
        { sqliteTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.UNKNOWN, ""),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    pumpEventsAndWaitForFuture(tableController.setUp())
    Disposer.register(disposable, tableController)

    // Act
    tableView.listeners.first().rowCountChanged("0")
    tableView.listeners.first().rowCountChanged("-1")
    tableView.listeners.first().rowCountChanged("nan")

    // Assert
    assertEquals(
      listOf(
        Pair("Row count must be a positive integer.", null),
        Pair("Row count must be a positive integer.", null),
        Pair("Row count must be a positive integer.", null),
      ),
      tableView.errorReported,
    )
  }

  private fun testUpdateWorksOnCustomDatabase(
    databaseFile: VirtualFile,
    targetTableName: String,
    targetColumnName: String,
    expectedSqliteStatement: String,
  ) {
    // Prepare
    customDatabaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          disposable,
          databaseFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )
    val databaseConnectionWrapper = DatabaseConnectionWrapper(customDatabaseConnection!!)

    val customDatabaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(databaseFile))
    runDispatching {
      databaseRepository.addDatabaseConnection(customDatabaseId, databaseConnectionWrapper)
    }

    val schema = pumpEventsAndWaitForFuture(customDatabaseConnection!!.readSchema())
    val targetTable = schema.tables.find { it.name == targetTableName }!!
    val targetCol = targetTable.columns.find { it.name == targetColumnName }!!

    val newValue = SqliteValue.StringValue("test value")

    val tableController =
      TableController(
        project,
        10,
        tableView,
        customDatabaseId,
        { targetTable },
        databaseRepository,
        SqliteStatement(SqliteStatementType.SELECT, selectAllAndRowIdFromTable(targetTable)),
        {},
        {},
        edtExecutor,
        edtExecutor,
      )
    Disposer.register(disposable, tableController)
    pumpEventsAndWaitForFuture(tableController.setUp())

    // Act
    tableView.listeners
      .first()
      .updateCellInvoked(0, targetCol.toResultSetCol().toViewColumn(), newValue)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    val sqliteResultSet =
      pumpEventsAndWaitForFuture(
        customDatabaseConnection!!.query(
          SqliteStatement(
            SqliteStatementType.SELECT,
            "SELECT * FROM ${AndroidSqlLexer.getValidName(targetTableName)}",
          )
        )
      )
    val rows = pumpEventsAndWaitForFuture(sqliteResultSet.getRowBatch(0, 1)).rows
    val value = rows.first().values.first { it.columnName == targetCol.name }.value
    assertEquals(SqliteValue.StringValue("test value"), value)
    val executedUpdateStatement =
      databaseConnectionWrapper.executedSqliteStatements.first { it.startsWith("UPDATE") }
    assertEquals(expectedSqliteStatement, executedUpdateStatement)
  }

  private fun SqliteColumn.toResultSetCol(): ResultSetSqliteColumn {
    return ResultSetSqliteColumn(name, affinity, isNullable, inPrimaryKey)
  }

  private fun assertRowSequence(
    invocations: List<List<SqliteRow>>,
    expectedInvocations: List<List<SqliteValue>>,
  ) {
    assertTrue(invocations.size == expectedInvocations.size)
    invocations.forEachIndexed { index, rows ->
      assertEquals(expectedInvocations[index][0], rows.first().values[0].value)
      assertEquals(expectedInvocations[index][1], rows.last().values[0].value)
    }
  }

  private fun List<SqliteRow>.toCellUpdates(): List<RowDiffOperation.UpdateCell> {
    val result = mutableListOf<RowDiffOperation.UpdateCell>()

    for (rowIndex in indices) {
      result.addAll(
        get(rowIndex).values.mapIndexed { colIndex, value ->
          RowDiffOperation.UpdateCell(value, rowIndex, colIndex)
        }
      )
    }

    return result
  }

  private fun List<SqliteColumn>.toViewColumns(): List<ViewColumn> = map {
    ViewColumn(it.name, it.inPrimaryKey, it.isNullable)
  }
}
