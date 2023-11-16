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
package com.android.tools.idea.sqlite.databaseConnection.live

import androidx.sqlite.inspection.SqliteInspectorProtocol
import androidx.sqlite.inspection.SqliteInspectorProtocol.GetSchemaResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.QueryResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorMessenger
import com.android.tools.idea.sqlite.createErrorSideChannel
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteValue
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class LiveDatabaseConnectionTest : LightPlatformTestCase() {
  private val taskExecutor: FutureCallbackExecutor =
    FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)
  private lateinit var liveDatabaseConnection: LiveDatabaseConnection
  private lateinit var scope: CoroutineScope

  override fun setUp() {
    super.setUp()
    scope = CoroutineScope(taskExecutor.asCoroutineDispatcher() + SupervisorJob())
  }

  override fun tearDown() {
    try {
      Disposer.dispose(liveDatabaseConnection)
      scope.cancel()
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  fun testReadSchema() =
    runBlocking<Unit> {
      // Prepare
      val column1 =
        SqliteInspectorProtocol.Column.newBuilder().setName("column1").setType("TEXT").build()

      val column2 =
        SqliteInspectorProtocol.Column.newBuilder().setName("column2").setType("INTEGER").build()

      val column3 =
        SqliteInspectorProtocol.Column.newBuilder().setName("column3").setType("FLOAT").build()

      val column4 =
        SqliteInspectorProtocol.Column.newBuilder().setName("column4").setType("BLOB").build()

      val table =
        SqliteInspectorProtocol.Table.newBuilder()
          .addColumns(column1)
          .addColumns(column2)
          .addColumns(column3)
          .addColumns(column4)
          .build()

      val schema = GetSchemaResponse.newBuilder().addTables(table).build()

      val schemaResponse = Response.newBuilder().setGetSchema(schema).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(schemaResponse.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val sqliteSchema = pumpEventsAndWaitForFuture(liveDatabaseConnection.readSchema())

      // Assert
      assertSize(1, sqliteSchema.tables)
      assertSize(4, sqliteSchema.tables.first().columns)
      assertEquals(RowIdName._ROWID_, sqliteSchema.tables.first().rowIdName)
      assertEquals("column1", sqliteSchema.tables.first().columns[0].name)
      assertEquals("column2", sqliteSchema.tables.first().columns[1].name)
      assertEquals("column3", sqliteSchema.tables.first().columns[2].name)
      assertEquals("column4", sqliteSchema.tables.first().columns[3].name)
      assertEquals(SqliteAffinity.TEXT, sqliteSchema.tables.first().columns[0].affinity)
      assertEquals(SqliteAffinity.INTEGER, sqliteSchema.tables.first().columns[1].affinity)
      assertEquals(SqliteAffinity.REAL, sqliteSchema.tables.first().columns[2].affinity)
      assertEquals(SqliteAffinity.BLOB, sqliteSchema.tables.first().columns[3].affinity)
    }

  fun testExecuteQuery() =
    runBlocking<Unit> {
      val largeFloat = Float.MAX_VALUE * 2.0
      val largeInteger = Long.MAX_VALUE

      // Prepare
      val cellValueString =
        SqliteInspectorProtocol.CellValue.newBuilder().setStringValue("a string").build()

      val cellValueFloat =
        SqliteInspectorProtocol.CellValue.newBuilder().setDoubleValue(largeFloat).build()

      val cellValueBlob =
        SqliteInspectorProtocol.CellValue.newBuilder()
          .setBlobValue(ByteString.copyFrom("a blob".toByteArray()))
          .build()

      val cellValueInt =
        SqliteInspectorProtocol.CellValue.newBuilder().setLongValue(largeInteger).build()

      val cellValueNull = SqliteInspectorProtocol.CellValue.newBuilder().build()

      val columnNames = listOf("column1", "column2", "column3", "column4", "column5")

      val rows =
        SqliteInspectorProtocol.Row.newBuilder()
          .addValues(cellValueString)
          .addValues(cellValueFloat)
          .addValues(cellValueBlob)
          .addValues(cellValueInt)
          .addValues(cellValueNull)
          .build()

      val cursor =
        Response.newBuilder()
          .setQuery(QueryResponse.newBuilder().addAllColumnNames(columnNames).addRows(rows))
          .build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "fake query"))
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1))

      assertSize(1, sqliteRows)
      assertSize(5, sqliteColumns)

      assertEquals("column1", sqliteColumns[0].name)
      assertEquals("column2", sqliteColumns[1].name)
      assertEquals("column3", sqliteColumns[2].name)
      assertEquals("column4", sqliteColumns[3].name)
      assertEquals("column5", sqliteColumns[4].name)

      assertNull(sqliteColumns[0].affinity)
      assertNull(sqliteColumns[1].affinity)
      assertNull(sqliteColumns[2].affinity)
      assertNull(sqliteColumns[3].affinity)
      assertNull(sqliteColumns[4].affinity)

      assertEquals(sqliteRows[0].values[0].value, SqliteValue.StringValue("a string"))
      assertEquals(sqliteRows[0].values[1].value, SqliteValue.StringValue(largeFloat.toString()))
      // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
      assertEquals(sqliteRows[0].values[2].value, SqliteValue.StringValue("6120626C6F62"))
      assertEquals(sqliteRows[0].values[3].value, SqliteValue.StringValue(largeInteger.toString()))
      assertEquals(sqliteRows[0].values[4].value, SqliteValue.NullValue)
    }

  fun testExecuteExplain() =
    runBlocking<Unit> {
      // Prepare
      val cellValueString =
        SqliteInspectorProtocol.CellValue.newBuilder().setStringValue("a string").build()

      val cellValueFloat =
        SqliteInspectorProtocol.CellValue.newBuilder().setDoubleValue(1.0).build()

      val cellValueBlob =
        SqliteInspectorProtocol.CellValue.newBuilder()
          .setBlobValue(ByteString.copyFrom("a blob".toByteArray()))
          .build()

      val cellValueInt = SqliteInspectorProtocol.CellValue.newBuilder().setLongValue(1).build()

      val cellValueNull = SqliteInspectorProtocol.CellValue.newBuilder().build()

      val columnNames = listOf("column1", "column2", "column3", "column4", "column5")

      val rows =
        SqliteInspectorProtocol.Row.newBuilder()
          .addValues(cellValueString)
          .addValues(cellValueFloat)
          .addValues(cellValueBlob)
          .addValues(cellValueInt)
          .addValues(cellValueNull)
          .build()

      val cursor =
        Response.newBuilder()
          .setQuery(QueryResponse.newBuilder().addAllColumnNames(columnNames).addRows(rows))
          .build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(SqliteStatement(SqliteStatementType.EXPLAIN, "fake query"))
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1))

      assertSize(1, sqliteRows)
      assertSize(5, sqliteColumns)

      assertEquals("column1", sqliteColumns[0].name)
      assertEquals("column2", sqliteColumns[1].name)
      assertEquals("column3", sqliteColumns[2].name)
      assertEquals("column4", sqliteColumns[3].name)
      assertEquals("column5", sqliteColumns[4].name)

      assertNull(sqliteColumns[0].affinity)
      assertNull(sqliteColumns[1].affinity)
      assertNull(sqliteColumns[2].affinity)
      assertNull(sqliteColumns[3].affinity)
      assertNull(sqliteColumns[4].affinity)

      assertEquals(sqliteRows[0].values[0].value, SqliteValue.StringValue("a string"))
      assertEquals(sqliteRows[0].values[1].value, SqliteValue.StringValue(1f.toString()))
      // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
      assertEquals(sqliteRows[0].values[2].value, SqliteValue.StringValue("6120626C6F62"))
      assertEquals(sqliteRows[0].values[3].value, SqliteValue.StringValue(1.toString()))
      assertEquals(sqliteRows[0].values[4].value, SqliteValue.NullValue)
    }

  fun testExecutePragma() =
    runBlocking<Unit> {
      // Prepare
      val cellValueString =
        SqliteInspectorProtocol.CellValue.newBuilder().setStringValue("a string").build()
      val cellValueFloat =
        SqliteInspectorProtocol.CellValue.newBuilder().setDoubleValue(1.0).build()
      val cellValueBlob =
        SqliteInspectorProtocol.CellValue.newBuilder()
          .setBlobValue(ByteString.copyFrom("a blob".toByteArray()))
          .build()
      val cellValueInt = SqliteInspectorProtocol.CellValue.newBuilder().setLongValue(1).build()
      val cellValueNull = SqliteInspectorProtocol.CellValue.newBuilder().build()

      val columnNames = listOf("column1", "column2", "column3", "column4", "column5")

      val rows =
        SqliteInspectorProtocol.Row.newBuilder()
          .addValues(cellValueString)
          .addValues(cellValueFloat)
          .addValues(cellValueBlob)
          .addValues(cellValueInt)
          .addValues(cellValueNull)
          .build()

      val cursor =
        Response.newBuilder()
          .setQuery(QueryResponse.newBuilder().addAllColumnNames(columnNames).addRows(rows))
          .build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(
            SqliteStatement(SqliteStatementType.PRAGMA_QUERY, "fake query")
          )
        )

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1))

      assertSize(1, sqliteRows)
      assertSize(5, sqliteColumns)

      assertEquals("column1", sqliteColumns[0].name)
      assertEquals("column2", sqliteColumns[1].name)
      assertEquals("column3", sqliteColumns[2].name)
      assertEquals("column4", sqliteColumns[3].name)
      assertEquals("column5", sqliteColumns[4].name)

      assertNull(sqliteColumns[0].affinity)
      assertNull(sqliteColumns[1].affinity)
      assertNull(sqliteColumns[2].affinity)
      assertNull(sqliteColumns[3].affinity)
      assertNull(sqliteColumns[4].affinity)

      assertEquals(sqliteRows[0].values[0].value, SqliteValue.StringValue("a string"))
      assertEquals(sqliteRows[0].values[1].value, SqliteValue.StringValue(1f.toString()))
      // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
      assertEquals(sqliteRows[0].values[2].value, SqliteValue.StringValue("6120626C6F62"))
      assertEquals(sqliteRows[0].values[3].value, SqliteValue.StringValue(1.toString()))
      assertEquals(sqliteRows[0].values[4].value, SqliteValue.NullValue)
    }

  fun testExecuteStatementWithParameters() =
    runBlocking<Unit> {
      // Prepare
      val mockMessenger = mock(AppInspectorMessenger::class.java)
      val sqliteStatement =
        SqliteStatement(
          SqliteStatementType.UNKNOWN,
          "fake query",
          listOf(SqliteValue.StringValue("1"), SqliteValue.NullValue),
          "fakeQuery"
        )

      val cursor = Response.newBuilder().setQuery(QueryResponse.newBuilder()).build()

      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      pumpEventsAndWaitForFuture(liveDatabaseConnection.execute(sqliteStatement))!!

      // Assert
      val param1 =
        SqliteInspectorProtocol.QueryParameterValue.newBuilder().setStringValue("1").build()
      val paramNull = SqliteInspectorProtocol.QueryParameterValue.newBuilder().build()

      val queryBuilder =
        SqliteInspectorProtocol.QueryCommand.newBuilder()
          .setQuery(sqliteStatement.sqliteStatementText)
          .addAllQueryParameterValues(listOf(param1, paramNull))
          .setDatabaseId(1)

      val queryCommand = SqliteInspectorProtocol.Command.newBuilder().setQuery(queryBuilder).build()

      verify(mockMessenger).sendRawCommand(queryCommand.toByteArray())
    }

  fun testReturnsEmptyResultSetForEmptyResponse() =
    runBlocking<Unit> {
      // Prepare
      val cursor = Response.newBuilder().build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "fake query"))
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1))

      assertSize(0, sqliteRows)
      assertSize(0, sqliteColumns)
    }

  fun testThrowsRecoverableErrorOnErrorOccurredResponse() =
    runBlocking<Unit> {
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

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      val error1 = pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      val error2 =
        pumpEventsAndWaitForFutureException(
          liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
        )

      assertEquals(error1.cause, error2.cause)
      assertInstanceOf(error1.cause, LiveInspectorException::class.java)
      assertEquals("errorMessage", error1.cause!!.message)
      assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
    }

  fun testThrowsNonRecoverableErrorOnErrorOccurredResponse() =
    runBlocking<Unit> {
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

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      val error1 = pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      val error2 =
        pumpEventsAndWaitForFutureException(
          liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
        )

      assertEquals(error1.cause, error2.cause)
      assertInstanceOf(error1.cause, LiveInspectorException::class.java)
      assertEquals(
        "An error has occurred which requires you to restart your app: errorMessage",
        error1.cause!!.message
      )
      assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
    }

  fun testThrowsUnknownRecoverableErrorOnErrorOccurredResponse() =
    runBlocking<Unit> {
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

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      val error1 = pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      val error2 =
        pumpEventsAndWaitForFutureException(
          liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
        )

      assertEquals(error1.cause, error2.cause)
      assertInstanceOf(error1.cause, LiveInspectorException::class.java)
      assertEquals(
        "An error has occurred which might require you to restart your app: errorMessage",
        error1.cause!!.message
      )
      assertEquals("stackTrace", (error1.cause as LiveInspectorException).onDeviceStackTrace)
    }

  fun testRecoverableErrorAnalytics() =
    runBlocking<Unit> {
      // Prepare
      val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
      project.registerServiceInstance(
        DatabaseInspectorAnalyticsTracker::class.java,
        mockTrackerService
      )

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

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      pumpEventsAndWaitForFutureException(
        liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
      )

      verify(mockTrackerService, times(2))
        .trackErrorOccurred(AppInspectionEvent.DatabaseInspectorEvent.ErrorKind.IS_RECOVERABLE_TRUE)
    }

  fun testNonRecoverableErrorAnalytics() =
    runBlocking<Unit> {
      // Prepare
      val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
      project.registerServiceInstance(
        DatabaseInspectorAnalyticsTracker::class.java,
        mockTrackerService
      )

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

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      pumpEventsAndWaitForFutureException(
        liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
      )

      verify(mockTrackerService, times(2))
        .trackErrorOccurred(
          AppInspectionEvent.DatabaseInspectorEvent.ErrorKind.IS_RECOVERABLE_FALSE
        )
    }

  fun testUnknownRecoverableErrorAnalytics() =
    runBlocking<Unit> {
      // Prepare
      val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
      project.registerServiceInstance(
        DatabaseInspectorAnalyticsTracker::class.java,
        mockTrackerService
      )

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

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      pumpEventsAndWaitForFutureException(
        liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
      )

      verify(mockTrackerService, times(2))
        .trackErrorOccurred(
          AppInspectionEvent.DatabaseInspectorEvent.ErrorKind.IS_RECOVERABLE_UNKNOWN
        )
    }

  fun testErrorNoExistingDbIsNotReportedInAnalytics() =
    runBlocking<Unit> {
      // Prepare
      val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
      project.registerServiceInstance(
        DatabaseInspectorAnalyticsTracker::class.java,
        mockTrackerService
      )

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
              .setErrorCode(
                SqliteInspectorProtocol.ErrorContent.ErrorCode
                  .ERROR_NO_OPEN_DATABASE_WITH_REQUESTED_ID
              )
              .build()
          )
          .build()

      val cursor = Response.newBuilder().setErrorOccurred(errorOccurredEvent).build()

      val mockMessenger = mock(AppInspectorMessenger::class.java)
      whenever(mockMessenger.sendRawCommand(any(ByteArray::class.java)))
        .thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())

      verifyNoMoreInteractions(mockTrackerService)
    }

  private fun createLiveDatabaseConnection(
    messenger: AppInspectorMessenger
  ): LiveDatabaseConnection {
    return LiveDatabaseConnection(
      testRootDisposable,
      DatabaseInspectorMessenger(messenger, scope, taskExecutor, createErrorSideChannel(project)),
      1,
      taskExecutor
    )
  }
}
