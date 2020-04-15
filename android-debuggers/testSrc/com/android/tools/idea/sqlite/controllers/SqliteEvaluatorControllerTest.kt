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
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureCancellation
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.EmptySqliteResultSet
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.mocks.MockDatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.mocks.MockSqliteEvaluatorView
import com.android.tools.idea.sqlite.mocks.MockSqliteResultSet
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.tableView.RowDiffOperation
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.Executor

class SqliteEvaluatorControllerTest : PlatformTestCase() {

  private lateinit var sqliteEvaluatorView: MockSqliteEvaluatorView
  private lateinit var mockDatabaseConnection: DatabaseConnection
  private lateinit var edtExecutor: Executor
  private lateinit var sqliteEvaluatorController: SqliteEvaluatorController
  private lateinit var sqliteDatabase: SqliteDatabase
  private lateinit var viewFactory: MockDatabaseInspectorViewsFactory

  override fun setUp() {
    super.setUp()
    mockDatabaseConnection = mock(DatabaseConnection::class.java)
    edtExecutor = EdtExecutorService.getInstance()
    viewFactory = MockDatabaseInspectorViewsFactory()
    sqliteEvaluatorView = viewFactory.sqliteEvaluatorView
    sqliteEvaluatorController = SqliteEvaluatorController(
      myProject,
      sqliteEvaluatorView,
      viewFactory,
      {},
      edtExecutor,
      edtExecutor
    )
    Disposer.register(testRootDisposable, sqliteEvaluatorController)

    sqliteDatabase = LiveSqliteDatabase("db", mockDatabaseConnection)
  }

  fun testSetUp() {
    // Act
    sqliteEvaluatorController.setUp()

    // Assert
    verify(sqliteEvaluatorView).addListener(any(SqliteEvaluatorView.Listener::class.java))
  }

  fun testEvaluateSqlActionQuerySuccess() {
    // Prepare
    val sqlStatement = SqliteStatement("SELECT")
    `when`(mockDatabaseConnection.execute(sqlStatement)).thenReturn(Futures.immediateFuture(EmptySqliteResultSet()))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, sqlStatement)

    // Assert
    verify(mockDatabaseConnection).execute(sqlStatement)
  }

  fun testEvaluateSqlActionQueryFailure() {
    // Prepare
    val sqlStatement = SqliteStatement("SELECT")
    val throwable = Throwable()
    `when`(mockDatabaseConnection.execute(sqlStatement)).thenReturn(Futures.immediateFailedFuture(throwable))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, sqlStatement)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseConnection).execute(sqlStatement)
    verify(sqliteEvaluatorView.tableView).reportError(eq("Error executing SQLite statement"), refEq(throwable))
  }

  fun testEvaluateStatementWithoutParametersDoesntShowParamsBindingDialog() {
    // Prepare
    val parametersBindingDialogView = viewFactory.parametersBindingDialogView
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(EmptySqliteResultSet()))
    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorView.listeners.first().evaluateSqlActionInvoked(sqliteDatabase, "SELECT * FROM foo WHERE id = 42")

    // Assert
    verify(parametersBindingDialogView, times(0)).show()
  }

  fun testEvaluateSqlActionCreateSuccess() {
    evaluateSqlActionSuccess("CREATE")
  }

  fun testEvaluateSqlActionCreateFailure() {
    evaluateSqlActionFailure("CREATE")
  }

  fun testEvaluateSqlActionDropSuccess() {
    evaluateSqlActionSuccess("DROP")
  }

  fun testEvaluateSqlActionDropFailure() {
    evaluateSqlActionFailure("DROP")
  }

  fun testEvaluateSqlActionAlterSuccess() {
    evaluateSqlActionSuccess("ALTER")
  }

  fun testEvaluateSqlActionAlterFailure() {
    evaluateSqlActionFailure("ALTER")
  }

  fun testEvaluateSqlActionInsertSuccess() {
    evaluateSqlActionSuccess("INSERT")
  }

  fun testEvaluateSqlActionInsertFailure() {
    evaluateSqlActionFailure("INSERT")
  }

  fun testEvaluateSqlActionUpdateSuccess() {
    evaluateSqlActionSuccess("UPDATE")
  }

  fun testEvaluateSqlActionUpdateFailure() {
    evaluateSqlActionFailure("UPDATE")
  }

  fun testEvaluateSqlActionDeleteSuccess() {
    evaluateSqlActionSuccess("DELETE")
  }

  fun testEvaluateSqlActionDeleteFailure() {
    evaluateSqlActionFailure("DELETE")
  }

  fun testTableViewIsNotShownIfResultSetIsEmpty() {
    // Prepare
    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT")))
      .thenReturn(Futures.immediateFuture(EmptySqliteResultSet()))

    sqliteEvaluatorController.setUp()

    // Act
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    // Assert
    verify(sqliteEvaluatorView.tableView, times(0)).updateRows(emptyList())
  }

  fun testTableViewIsShownIfResultSetIsNotEmpty() {
    // Prepare
    val mockSqliteResultSet = MockSqliteResultSet(10)
    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT"))).thenReturn(Futures.immediateFuture(mockSqliteResultSet))

    sqliteEvaluatorController.setUp()

    // Act
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    // Assert
    verify(sqliteEvaluatorView.tableView).updateRows(mockSqliteResultSet.rows.map { RowDiffOperation.AddRow(it) })
  }

  fun testUpdateSchemaIsCalledEveryTimeAUserDefinedStatementIsExecuted() {
    // Prepare
    val emptyResultSet = MockSqliteResultSet(0)
    val nonEmptyResultSet = MockSqliteResultSet(10)

    val mockListener = mock(SqliteEvaluatorController.Listener::class.java)

    sqliteEvaluatorController.setUp()
    sqliteEvaluatorController.addListener(mockListener)

    // Act
    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT"))).thenReturn(Futures.immediateFuture(nonEmptyResultSet))
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT"))).thenReturn(Futures.immediateFuture(emptyResultSet))
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    // Assert
    verify(mockListener, times(2)).onSqliteStatementExecuted(sqliteDatabase)
  }

  fun testResetViewBeforePopulatingIt() {
    // Prepare
    val mockSqliteResultSet = MockSqliteResultSet(10)
    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT"))).thenReturn(Futures.immediateFuture(mockSqliteResultSet))

    sqliteEvaluatorController.setUp()

    val orderVerifier = inOrder(sqliteEvaluatorView.tableView)

    // Act
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    // Assert
    orderVerifier.verify(sqliteEvaluatorView.tableView).resetView()
    orderVerifier.verify(sqliteEvaluatorView.tableView).showTableColumns(mockSqliteResultSet._columns)
    orderVerifier.verify(sqliteEvaluatorView.tableView).updateRows(mockSqliteResultSet.rows.map { RowDiffOperation.AddRow(it) })
    orderVerifier.verify(sqliteEvaluatorView.tableView).resetView()
    orderVerifier.verify(sqliteEvaluatorView.tableView).showTableColumns(mockSqliteResultSet._columns)
    orderVerifier.verify(sqliteEvaluatorView.tableView).updateRows(mockSqliteResultSet.rows.map { RowDiffOperation.AddRow(it) })
  }

  fun testErrorFromRowCountAreHandled() {
    // Prepare
    val exception = RuntimeException()
    val mockResultSet = mock(SqliteResultSet::class.java)
    `when`(mockResultSet.totalRowCount).thenReturn(Futures.immediateFailedFuture(exception))

    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))

    sqliteEvaluatorController.setUp()

    // Act
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("fake stmt")))

    // Assert
    verify(sqliteEvaluatorView.tableView).reportError("Error executing SQLite statement", exception)
  }

  fun testRefreshData() {
    // Prepare
    val mockSqliteResultSet = MockSqliteResultSet(10)
    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT"))).thenReturn(Futures.immediateFuture(mockSqliteResultSet))

    sqliteEvaluatorController.setUp()
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    // Act
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.refreshData())

    // Assert
    verify(sqliteEvaluatorView.tableView, times(2)).startTableLoading()
  }

  fun testRefreshDataScheduledOneAtATime() {
    // Prepare
    val mockSqliteResultSet = MockSqliteResultSet(10)
    `when`(mockDatabaseConnection.execute(SqliteStatement("SELECT"))).thenReturn(Futures.immediateFuture(mockSqliteResultSet))

    sqliteEvaluatorController.setUp()
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("SELECT")))

    // Act
    val future1 = sqliteEvaluatorController.refreshData()
    val future2 = sqliteEvaluatorController.refreshData()
    pumpEventsAndWaitForFuture(future2)
    val future3 = sqliteEvaluatorController.refreshData()

    // Assert
    assertEquals(future1, future2)
    assertTrue(future2 != future3)
  }

  fun testDisposeCancelsExecution() {
    // Prepare
    val executeFuture = SettableFuture.create<SqliteResultSet>()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(executeFuture)
    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("fake stmt"))
    Disposer.dispose(sqliteEvaluatorController)
    // Assert
    pumpEventsAndWaitForFutureCancellation(executeFuture)
  }

  fun testDisposeCancelsRowCountQuery() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    val rowCountFuture = SettableFuture.create<Int>()
    `when`(mockResultSet.totalRowCount).thenReturn(rowCountFuture)
    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("fake stmt"))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    // verify that rowCountFuture is in use.
    verify(mockDatabaseConnection).execute(any(SqliteStatement::class.java))
    Disposer.dispose(sqliteEvaluatorController)
    // Assert
    pumpEventsAndWaitForFutureCancellation(rowCountFuture)
  }

  fun testEvaluateExpressionAnalytics() {
    // Prepare
    val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, mockTrackerService)

    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(EmptySqliteResultSet()))
    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorView.listeners.first().evaluateSqlActionInvoked(sqliteDatabase, "SELECT * FROM foo")

    // Assert
    verify(mockTrackerService).trackStatementExecuted(AppInspectionEvent.DatabaseInspectorEvent.StatementContext.USER_DEFINED_STATEMENT_CONTEXT)
  }

  fun testNotifyDataMightBeStaleUpdatesTable() {
    // Prepare
    val mockResultSet = MockSqliteResultSet()
    `when`(mockDatabaseConnection.execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mockResultSet))
    sqliteEvaluatorController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement("fake stmt")))

    viewFactory.tableView.listeners.first().toggleLiveUpdatesInvoked()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Act
    sqliteEvaluatorController.notifyDataMightBeStale()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    // 1st invocation by setUp, 2nd by toggleLiveUpdatesInvoked, 3rd by notifyDataMightBeStale
    verify(sqliteEvaluatorView.tableView, times(3)).showTableColumns(mockResultSet._columns)
    // invocation by setUp
    verify(sqliteEvaluatorView.tableView, times(1)).updateRows(mockResultSet.invocations[0].map { RowDiffOperation.AddRow(it) })
    // 1st by toggleLiveUpdatesInvoked, 2nd by notifyDataMightBeStale
    verify(sqliteEvaluatorView.tableView, times(2)).updateRows(emptyList())
    // invocation by setUp
    verify(sqliteEvaluatorView.tableView, times(1)).startTableLoading()
  }

  private fun evaluateSqlActionSuccess(action: String) {
    // Prepare
    `when`(mockDatabaseConnection.execute(SqliteStatement(action))).thenReturn(Futures.immediateFuture(EmptySqliteResultSet()))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement(action))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseConnection).execute(SqliteStatement(action))
    verify(sqliteEvaluatorView.tableView).resetView()
    verify(sqliteEvaluatorView.tableView).setEmptyText("The statement was run successfully.")
  }

  fun testOldTableControllerListenerIsRemoveFromViewWhenNewQueryIsExecuted() {
    // Prepare
    val sqlStatement1 = SqliteStatement("fake stmt1")
    val sqlStatement2 = SqliteStatement("fake stmt2")

    val mockSqliteResultSet1 = MockSqliteResultSet()
    val mockSqliteResultSet2 = MockSqliteResultSet(columns = listOf(ResultSetSqliteColumn("c1")))

    `when`(mockDatabaseConnection.execute(sqlStatement1)).thenReturn(Futures.immediateFuture(mockSqliteResultSet1))
    `when`(mockDatabaseConnection.execute(sqlStatement2)).thenReturn(Futures.immediateFuture(mockSqliteResultSet2))
    sqliteEvaluatorController.setUp()

    // Act
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, sqlStatement1))
    pumpEventsAndWaitForFuture(sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, sqlStatement2))
    viewFactory.tableView.listeners.forEach { it.refreshDataInvoked() }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(sqliteEvaluatorView.tableView).showTableColumns(mockSqliteResultSet1._columns)
    verify(sqliteEvaluatorView.tableView, times(2)).showTableColumns(mockSqliteResultSet2._columns)
  }

  private fun evaluateSqlActionFailure(action: String) {
    // Prepare
    val throwable = Throwable()
    `when`(mockDatabaseConnection.execute(SqliteStatement(action))).thenReturn(Futures.immediateFailedFuture(throwable))

    sqliteEvaluatorController.setUp()

    // Act
    sqliteEvaluatorController.evaluateSqlStatement(sqliteDatabase, SqliteStatement(action))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(mockDatabaseConnection).execute(SqliteStatement(action))
    verify(sqliteEvaluatorView.tableView).reportError(eq("Error executing SQLite statement"), refEq(throwable))
    verify(sqliteEvaluatorView.tableView).setEmptyText("An error occurred while running the statement.")
  }
}
