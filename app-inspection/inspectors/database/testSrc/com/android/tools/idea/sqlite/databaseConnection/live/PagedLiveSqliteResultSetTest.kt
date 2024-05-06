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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureCancellation
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.model.ResultSetSqliteColumn
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteValue
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertThrows
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor

class PagedLiveSqliteResultSetTest : LightPlatformTestCase() {
  private val taskExecutor = PooledThreadExecutor.INSTANCE
  private val edtExecutor = EdtExecutorService.getInstance()
  private val scope = CoroutineScope(edtExecutor.asCoroutineDispatcher() + SupervisorJob())

  class FakeMessenger(val originalQuery: String, val response: ByteArray) : AppInspectorMessenger {
    override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
      val parsed = SqliteInspectorProtocol.Command.parseFrom(rawData)
      assertNotSame(
        "In paged version of ResultSet we should never run the original query ",
        originalQuery,
        parsed.query.query
      )
      return response
    }

    override val eventFlow: Flow<ByteArray> = emptyFlow()

    override val scope: CoroutineScope
      get() = throw NotImplementedError()
  }

  fun testColumnsReturnCorrectListOfColumns() =
    runBlocking<Unit> {
      // Prepare
      val columnNames = listOf("col1", "col2")

      val cursor =
        SqliteInspectorProtocol.Response.newBuilder()
          .setQuery(
            SqliteInspectorProtocol.QueryResponse.newBuilder().addAllColumnNames(columnNames)
          )
          .build()

      val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
      val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
      val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

      // Act
      val columnsFromResultSet = pumpEventsAndWaitForFuture(resultSet.columns)

      // Assert
      assertEquals(
        listOf(ResultSetSqliteColumn("col1"), ResultSetSqliteColumn("col2")),
        columnsFromResultSet
      )
    }

  fun testRowCountReturnsCorrectNumberOfRows() =
    runBlocking<Unit> {
      // Prepare
      val rowCountCellValue =
        SqliteInspectorProtocol.CellValue.newBuilder().setLongValue(12345).build()

      val row = SqliteInspectorProtocol.Row.newBuilder().addValues(rowCountCellValue).build()

      val columnNames = listOf("COUNT(*)")

      val cursor =
        SqliteInspectorProtocol.Response.newBuilder()
          .setQuery(
            SqliteInspectorProtocol.QueryResponse.newBuilder()
              .addAllColumnNames(columnNames)
              .addRows(row)
          )
          .build()

      val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
      val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
      val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

      // Act
      val rowCount = pumpEventsAndWaitForFuture(resultSet.totalRowCount)

      // Assert
      assertEquals(12345, rowCount)
    }

  fun testRowCountFailsIfDisposed() {
    // Prepare
    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT COUNT(*) FROM (query)")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, ByteArray(0))
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)
    Disposer.register(project, resultSet)

    // Act / Assert
    Disposer.dispose(resultSet)
    pumpEventsAndWaitForFutureCancellation(resultSet.totalRowCount)
  }

  fun testGetRowBatchReturnsCorrectListOfRows() {
    // Prepare
    val cellValueString =
      SqliteInspectorProtocol.CellValue.newBuilder().setStringValue("a string").build()

    val row =
      SqliteInspectorProtocol.Row.newBuilder()
        .addValues(cellValueString)
        .addValues(cellValueString)
        .addValues(cellValueString)
        .build()

    val columnNames = listOf("column1", "column2", "column3")

    val cursor =
      SqliteInspectorProtocol.Response.newBuilder()
        .setQuery(
          SqliteInspectorProtocol.QueryResponse.newBuilder()
            .addAllColumnNames(columnNames)
            .addRows(row)
        )
        .build()

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

    // Act
    // Since we are mocking the answer the values passed to getRowBatch don't matter.
    val rowsFromResultSet = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, Integer.MAX_VALUE))

    // Assert
    assertSize(1, rowsFromResultSet)
    assertEquals("column1", rowsFromResultSet.first().values.first().columnName)
    assertEquals(
      SqliteValue.StringValue("a string"),
      rowsFromResultSet.first().values.first().value
    )
  }

  fun testGetRowBatchFailsIfDisposed() {
    // Prepare

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, ByteArray(0))
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

    Disposer.register(project, resultSet)

    // Act / Assert
    Disposer.dispose(resultSet)
    pumpEventsAndWaitForFutureCancellation(resultSet.getRowBatch(0, Integer.MAX_VALUE))
  }

  fun testGetRowBatchThrowsIfMinOffsetSmallerThanZero() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor =
      SqliteInspectorProtocol.Response.newBuilder()
        .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
        .build()

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

    // Act / Assert
    assertThrows(IllegalArgumentException::class.java) {
      resultSet.getRowBatch(-1, Integer.MAX_VALUE)
    }
  }

  fun testGetRowBatchThrowsIfMaxOffsetSmallerEqualZero() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor =
      SqliteInspectorProtocol.Response.newBuilder()
        .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
        .build()

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

    // Act / Assert
    assertThrows(IllegalArgumentException::class.java) { resultSet.getRowBatch(0, 0) }
  }

  fun testThrowsRecoverableErrorOnErrorOccurredResponse() {
    // Prepare
    val errorOccurredEvent =
      SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder()
        .setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setMessage("errorMessage")
            .setRecoverability(
              SqliteInspectorProtocol.ErrorRecoverability.newBuilder()
                .setIsRecoverable(true)
                .build()
            )
            .setStackTrace("stackTrace")
            .build()
        )
        .build()

    val cursor =
      SqliteInspectorProtocol.Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

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
    val errorOccurredEvent =
      SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder()
        .setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setMessage("errorMessage")
            .setRecoverability(
              SqliteInspectorProtocol.ErrorRecoverability.newBuilder()
                .setIsRecoverable(false)
                .build()
            )
            .setStackTrace("stackTrace")
            .build()
        )
        .build()

    val cursor =
      SqliteInspectorProtocol.Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

    // Act / Assert
    val error1 = pumpEventsAndWaitForFutureException(resultSet.columns)
    val error2 = pumpEventsAndWaitForFutureException(resultSet.totalRowCount)
    val error3 = pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0, 10))

    assertEquals(error1.cause, error2.cause)
    assertEquals(error1.cause, error3.cause)
    assertInstanceOf(error1.cause, LiveInspectorException::class.java)
    assertEquals(
      "An error has occurred which requires you to restart your app: errorMessage",
      error1.cause!!.message
    )
    assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
  }

  fun testThrowsUnknownRecoverableErrorOnErrorOccurredResponse() {
    // Prepare
    val errorOccurredEvent =
      SqliteInspectorProtocol.ErrorOccurredResponse.newBuilder()
        .setContent(
          SqliteInspectorProtocol.ErrorContent.newBuilder()
            .setMessage("errorMessage")
            .setRecoverability(SqliteInspectorProtocol.ErrorRecoverability.newBuilder().build())
            .setStackTrace("stackTrace")
            .build()
        )
        .build()

    val cursor =
      SqliteInspectorProtocol.Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

    val statement = SqliteStatement(SqliteStatementType.SELECT, "SELECT")
    val mockMessenger = FakeMessenger(statement.sqliteStatementText, cursor.toByteArray())
    val resultSet = createPagedLiveSqliteResultSet(statement, mockMessenger)

    // Act / Assert
    val error1 = pumpEventsAndWaitForFutureException(resultSet.columns)
    val error2 = pumpEventsAndWaitForFutureException(resultSet.totalRowCount)
    val error3 = pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0, 10))

    assertEquals(error1.cause, error2.cause)
    assertEquals(error1.cause, error3.cause)
    assertInstanceOf(error1.cause, LiveInspectorException::class.java)
    assertEquals(
      "An error has occurred which might require you to restart your app: errorMessage",
      error1.cause!!.message
    )
    assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
  }

  private fun createPagedLiveSqliteResultSet(
    statement: SqliteStatement,
    messenger: AppInspectorMessenger
  ): LiveSqliteResultSet {
    val liveSqliteResultSet =
      PagedLiveSqliteResultSet(
        statement,
        DatabaseInspectorMessenger(messenger, scope, taskExecutor),
        0,
        taskExecutor
      )
    Disposer.register(testRootDisposable, liveSqliteResultSet)
    return liveSqliteResultSet
  }
}
