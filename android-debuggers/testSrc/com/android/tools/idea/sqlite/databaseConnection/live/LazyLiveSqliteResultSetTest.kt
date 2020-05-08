/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureCancellation
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertThrows
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class LazyLiveSqliteResultSetTest : LightPlatformTestCase() {
  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)

  fun testColumnsReturnCorrectListOfColumns() {
    // Prepare
    val columnNames = listOf("col1", "col2")

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addAllColumnNames(columnNames))
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act
    val columnsFromResultSet = pumpEventsAndWaitForFuture(resultSet.columns)

    // Assert
    assertEquals(
      listOf(ResultSetSqliteColumn("col1"), ResultSetSqliteColumn("col2")),
      columnsFromResultSet
    )
  }

  fun testRowCountReturnsCorrectNumberOfRows() {
    // Prepare
    val cellValue = SqliteInspectorProtocol.CellValue.newBuilder()
      .setIntValue(1)
      .build()

    val row = SqliteInspectorProtocol.Row.newBuilder()
      .addValues(cellValue)
      .build()

    val columnNames = listOf("columnName")

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(
        SqliteInspectorProtocol.QueryResponse.newBuilder()
          .addAllColumnNames(columnNames)
          .addRows(row)
          .addRows(row)
      ).build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act
    val rowCount = pumpEventsAndWaitForFuture(resultSet.totalRowCount)

    // Assert
    assertEquals(2, rowCount)
  }

  fun testRowCountFailsIfDisposed() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(SettableFuture.create())

    val resultSet = createLazyLiveSqliteResultSet(
      SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger
    )
    Disposer.register(project, resultSet)

    // Act / Assert
    Disposer.dispose(resultSet)
    pumpEventsAndWaitForFutureCancellation(resultSet.totalRowCount)
  }

  fun testGetRowBatchReturnsCorrectListOfRows() {
    // Prepare
    val cellValueString = SqliteInspectorProtocol.CellValue.newBuilder()
      .setStringValue("a string")
      .build()

    val row = SqliteInspectorProtocol.Row.newBuilder()
      .addValues(cellValueString)
      .addValues(cellValueString)
      .addValues(cellValueString)
      .build()

    val columnNames = listOf("column1", "column2", "column3")

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addAllColumnNames(columnNames).addRows(row))
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act
    // Since we are mocking the answer the values passed to getRowBatch don't matter.
    val rowsFromResultSet = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, Integer.MAX_VALUE))

    // Assert
    assertSize(1, rowsFromResultSet)
    assertEquals("column1", rowsFromResultSet.first().values.first().columnName)
    assertEquals(SqliteValue.StringValue("a string"), rowsFromResultSet.first().values.first().value)
  }

  fun testGetRowBatchFailsIfDisposed() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(SettableFuture.create())

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)
    Disposer.register(project, resultSet)

    // Act / Assert
    Disposer.dispose(resultSet)
    pumpEventsAndWaitForFutureCancellation(resultSet.getRowBatch(0, Integer.MAX_VALUE))
  }

  fun testGetRowBatchThrowsIfMinOffsetSmallerThanZero() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act / Assert
    assertThrows<IllegalArgumentException>(IllegalArgumentException::class.java) {
      resultSet.getRowBatch(-1, Integer.MAX_VALUE)
    }
  }

  fun testGetRowBatchThrowsIfMaxOffsetSmallerEqualZero() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act / Assert
    assertThrows<IllegalArgumentException>(IllegalArgumentException::class.java) {
      resultSet.getRowBatch(0, 0)
    }
  }

  fun testThrowsRecoverableErrorOnErrorOccurredResponse() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().setIsRecoverable(true).build())
        .setStackTrace("stackTrace")
        .build()
    ).build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setErrorOccurred(errorOccurredEvent)
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act / Assert
    val error1 = pumpEventsAndWaitForFutureException(resultSet.columns)
    val error2 = pumpEventsAndWaitForFutureException(resultSet.totalRowCount)
    val error3 = pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0, 10))

    assertEquals(error1.cause, error2.cause)
    assertEquals(error1.cause, error3.cause)
    assertInstanceOf(error1.cause, LiveInspectorException::class.java)
    assertEquals("errorMessage", error1.cause!!.message)
    assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
  }

  fun testThrowsNonRecoverableErrorOnErrorOccurredResponse() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().setIsRecoverable(false).build())
        .setStackTrace("stackTrace")
        .build()
    ).build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setErrorOccurred(errorOccurredEvent)
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act / Assert
    val error1 = pumpEventsAndWaitForFutureException(resultSet.columns)
    val error2 = pumpEventsAndWaitForFutureException(resultSet.totalRowCount)
    val error3 = pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0, 10))

    assertEquals(error1.cause, error2.cause)
    assertEquals(error1.cause, error3.cause)
    assertInstanceOf(error1.cause, LiveInspectorException::class.java)
    assertEquals("An error has occurred which requires you to restart your app: errorMessage", error1.cause!!.message)
    assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
  }

  fun testThrowsUnknownRecoverableErrorOnErrorOccurredResponse() {
    // Prepare
    val errorOccurredEvent = SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder().setContent(
      SqliteInspectorProtocol.ErrorContent.newBuilder()
        .setMessage("errorMessage")
        .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().build())
        .setStackTrace("stackTrace")
        .build()
    ).build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setErrorOccurred(errorOccurredEvent)
      .build()

    val mockMessenger = mock(AppInspectorClient.CommandMessenger::class.java)
    `when`(mockMessenger.sendRawCommand(any(ByteArray::class.java))).thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val resultSet = createLazyLiveSqliteResultSet(SqliteStatement(SqliteStatementType.EXPLAIN, "fake stmt"), mockMessenger)

    // Act / Assert
    val error1 = pumpEventsAndWaitForFutureException(resultSet.columns)
    val error2 = pumpEventsAndWaitForFutureException(resultSet.totalRowCount)
    val error3 = pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0, 10))

    assertEquals(error1.cause, error2.cause)
    assertEquals(error1.cause, error3.cause)
    assertInstanceOf(error1.cause, LiveInspectorException::class.java)
    assertEquals("An error has occurred which might require you to restart your app: errorMessage", error1.cause!!.message)
    assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
  }

  private fun createLazyLiveSqliteResultSet(
    statement: SqliteStatement,
    messenger: AppInspectorClient.CommandMessenger
  ): LiveSqliteResultSet {
    val liveSqliteResultSet = LazyLiveSqliteResultSet(statement, DatabaseInspectorMessenger(messenger, taskExecutor), 0, taskExecutor)
    Disposer.register(testRootDisposable, liveSqliteResultSet)
    return liveSqliteResultSet
  }
}