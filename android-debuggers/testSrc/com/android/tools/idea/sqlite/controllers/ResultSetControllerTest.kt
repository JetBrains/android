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
import com.android.tools.idea.sqlite.mocks.MockSqliteResultSet
import com.android.tools.idea.sqlite.mocks.MockTableView
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
import org.mockito.InOrder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class ResultSetControllerTest : UsefulTestCase() {

  private lateinit var tableView: MockTableView
  private lateinit var mockResultSet: MockSqliteResultSet
  private lateinit var edtExecutor: FutureCallbackExecutor
  private lateinit var resultSetController: ResultSetController
  private lateinit var orderVerifier: InOrder

  override fun setUp() {
    super.setUp()
    tableView = spy(MockTableView::class.java)
    mockResultSet = MockSqliteResultSet()
    edtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
    orderVerifier = inOrder(tableView)
  }

  fun testSetUp() {
    // Prepare
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    orderVerifier.verify(tableView).startTableLoading()
    orderVerifier.verify(tableView).showTableColumns(mockResultSet._columns)
    orderVerifier.verify(tableView).showTableRowBatch(mockResultSet.invocations[0])
    orderVerifier.verify(tableView).stopTableLoading()

    verify(tableView, times(0)).reportError(any(String::class.java), any(Throwable::class.java))
  }

  fun testSetUpTableNameIsNull() {
    // Prepare
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, null, mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()

    // Assert
    verify(tableView).startTableLoading()
  }

  fun testSetUpError() {
    // Prepare
    val mockResultSet = mock(SqliteResultSet::class.java)
    val throwable = Throwable()
    `when`(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(throwable))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

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
    val mockResultSet = mock(SqliteResultSet::class.java)
    `when`(mockResultSet.columns).thenReturn(Futures.immediateFailedFuture(Throwable()))
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act/Assert
    Disposer.dispose(resultSetController)
    assertThrows<ProcessCanceledException>(ProcessCanceledException::class.java) { resultSetController.setUp() }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnSetup`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(10)
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert
    verify(tableView, times(2)).setFetchNextRowsButtonState(false)
  }

  fun `test Next UiIsDisabledWhenNoMoreRowsAvailableOnNext`() {
    // Prepare
    val mockResultSet = MockSqliteResultSet(2)
    resultSetController = ResultSetController(testRootDisposable, 1, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 5, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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
    resultSetController = ResultSetController(testRootDisposable, 10, tableView, "tableName", mockResultSet, edtExecutor)

    // Act
    resultSetController.setUp()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
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

  private fun assertRowSequence(invocations: List<List<SqliteRow>>, expectedInvocations: List<List<Int>>) {
    assertTrue(invocations.size == expectedInvocations.size)
    invocations.forEachIndexed { index, rows ->
      assertEquals(expectedInvocations[index][0], rows.first().values[0].value)
      assertEquals(expectedInvocations[index][1], rows.last().values[0].value)
    }
  }
}
