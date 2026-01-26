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
import com.google.common.truth.Truth.assertThat
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
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(schemaResponse.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val sqliteSchema = pumpEventsAndWaitForFuture(liveDatabaseConnection.readSchema())

      // Assert
      assertThat(sqliteSchema.tables).hasSize(1)
      assertThat(sqliteSchema.tables.first().columns).hasSize(4)
      assertThat(sqliteSchema.tables.first().rowIdName).isEqualTo(RowIdName._ROWID_)
      assertThat(sqliteSchema.tables.first().columns[0].name).isEqualTo("column1")
      assertThat(sqliteSchema.tables.first().columns[1].name).isEqualTo("column2")
      assertThat(sqliteSchema.tables.first().columns[2].name).isEqualTo("column3")
      assertThat(sqliteSchema.tables.first().columns[3].name).isEqualTo("column4")
      assertThat(sqliteSchema.tables.first().columns[0].affinity).isEqualTo(SqliteAffinity.TEXT)
      assertThat(sqliteSchema.tables.first().columns[1].affinity).isEqualTo(SqliteAffinity.INTEGER)
      assertThat(sqliteSchema.tables.first().columns[2].affinity).isEqualTo(SqliteAffinity.REAL)
      assertThat(sqliteSchema.tables.first().columns[3].affinity).isEqualTo(SqliteAffinity.BLOB)
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "fake query"))
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1)).rows

      assertThat(sqliteRows).hasSize(1)
      assertThat(sqliteColumns).hasSize(5)

      assertThat(sqliteColumns[0].name).isEqualTo("column1")
      assertThat(sqliteColumns[1].name).isEqualTo("column2")
      assertThat(sqliteColumns[2].name).isEqualTo("column3")
      assertThat(sqliteColumns[3].name).isEqualTo("column4")
      assertThat(sqliteColumns[4].name).isEqualTo("column5")

      assertThat(sqliteColumns[0].affinity).isNull()
      assertThat(sqliteColumns[1].affinity).isNull()
      assertThat(sqliteColumns[2].affinity).isNull()
      assertThat(sqliteColumns[3].affinity).isNull()
      assertThat(sqliteColumns[4].affinity).isNull()

      assertThat(sqliteRows[0].values[0].value).isEqualTo(SqliteValue.StringValue("a string"))
      assertThat(sqliteRows[0].values[1].value)
        .isEqualTo(SqliteValue.StringValue(largeFloat.toString()))
      // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
      assertThat(sqliteRows[0].values[2].value).isEqualTo(SqliteValue.StringValue("6120626C6F62"))
      assertThat(sqliteRows[0].values[3].value)
        .isEqualTo(SqliteValue.StringValue(largeInteger.toString()))
      assertThat(sqliteRows[0].values[4].value).isEqualTo(SqliteValue.NullValue)
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(SqliteStatement(SqliteStatementType.EXPLAIN, "fake query"))
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1)).rows

      assertThat(sqliteRows).hasSize(1)
      assertThat(sqliteColumns).hasSize(5)

      assertThat(sqliteColumns[0].name).isEqualTo("column1")
      assertThat(sqliteColumns[1].name).isEqualTo("column2")
      assertThat(sqliteColumns[2].name).isEqualTo("column3")
      assertThat(sqliteColumns[3].name).isEqualTo("column4")
      assertThat(sqliteColumns[4].name).isEqualTo("column5")

      assertThat(sqliteColumns[0].affinity).isNull()
      assertThat(sqliteColumns[1].affinity).isNull()
      assertThat(sqliteColumns[2].affinity).isNull()
      assertThat(sqliteColumns[3].affinity).isNull()
      assertThat(sqliteColumns[4].affinity).isNull()

      assertThat(sqliteRows[0].values[0].value).isEqualTo(SqliteValue.StringValue("a string"))
      assertThat(sqliteRows[0].values[1].value).isEqualTo(SqliteValue.StringValue(1f.toString()))
      // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
      assertThat(sqliteRows[0].values[2].value).isEqualTo(SqliteValue.StringValue("6120626C6F62"))
      assertThat(sqliteRows[0].values[3].value).isEqualTo(SqliteValue.StringValue(1.toString()))
      assertThat(sqliteRows[0].values[4].value).isEqualTo(SqliteValue.NullValue)
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(
            SqliteStatement(SqliteStatementType.PRAGMA_QUERY, "fake query")
          )
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1)).rows

      assertThat(sqliteRows).hasSize(1)
      assertThat(sqliteColumns).hasSize(5)

      assertThat(sqliteColumns[0].name).isEqualTo("column1")
      assertThat(sqliteColumns[1].name).isEqualTo("column2")
      assertThat(sqliteColumns[2].name).isEqualTo("column3")
      assertThat(sqliteColumns[3].name).isEqualTo("column4")
      assertThat(sqliteColumns[4].name).isEqualTo("column5")

      assertThat(sqliteColumns[0].affinity).isNull()
      assertThat(sqliteColumns[1].affinity).isNull()
      assertThat(sqliteColumns[2].affinity).isNull()
      assertThat(sqliteColumns[3].affinity).isNull()
      assertThat(sqliteColumns[4].affinity).isNull()

      assertThat(sqliteRows[0].values[0].value).isEqualTo(SqliteValue.StringValue("a string"))
      assertThat(sqliteRows[0].values[1].value).isEqualTo(SqliteValue.StringValue(1f.toString()))
      // the value for the blob corresponds to the base16 encoding of the byte array of the blob.
      assertThat(sqliteRows[0].values[2].value).isEqualTo(SqliteValue.StringValue("6120626C6F62"))
      assertThat(sqliteRows[0].values[3].value).isEqualTo(SqliteValue.StringValue(1.toString()))
      assertThat(sqliteRows[0].values[4].value).isEqualTo(SqliteValue.NullValue)
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
          "fakeQuery",
        )

      val cursor = Response.newBuilder().setQuery(QueryResponse.newBuilder()).build()

      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act
      val resultSet =
        pumpEventsAndWaitForFuture(
          liveDatabaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "fake query"))
        )!!

      // Assert
      val sqliteColumns = pumpEventsAndWaitForFuture(resultSet.columns)
      val sqliteRows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1)).rows

      assertThat(sqliteRows).isEmpty()
      assertThat(sqliteColumns).isEmpty()
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      val error1 = pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      val error2 =
        pumpEventsAndWaitForFutureException(
          liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
        )

      assertThat(error2.cause).isEqualTo(error1.cause)
      assertThat(error1.cause).isInstanceOf(LiveInspectorException::class.java)
      assertThat(error1.cause!!.message).isEqualTo("errorMessage")
      assertThat((error1.cause as LiveInspectorException).onDeviceStackTrace)
        .isEqualTo("stackTrace")
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      val error1 = pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      val error2 =
        pumpEventsAndWaitForFutureException(
          liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
        )

      assertThat(error2.cause).isEqualTo(error1.cause)
      assertThat(error1.cause).isInstanceOf(LiveInspectorException::class.java)
      assertThat(error1.cause!!.message)
        .isEqualTo("An error has occurred which requires you to restart your app: errorMessage")
      assertThat((error1.cause as LiveInspectorException).onDeviceStackTrace)
        .isEqualTo("stackTrace")
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

      liveDatabaseConnection = createLiveDatabaseConnection(mockMessenger)

      // Act / Assert
      val error1 = pumpEventsAndWaitForFutureException(liveDatabaseConnection.readSchema())
      val error2 =
        pumpEventsAndWaitForFutureException(
          liveDatabaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "fake query"))
        )

      assertThat(error2.cause).isEqualTo(error1.cause)
      assertThat(error1.cause).isInstanceOf(LiveInspectorException::class.java)
      assertThat(error1.cause!!.message)
        .isEqualTo(
          "An error has occurred which might require you to restart your app: errorMessage"
        )
      assertThat((error1.cause as LiveInspectorException).onDeviceStackTrace)
        .isEqualTo("stackTrace")
    }

  fun testRecoverableErrorAnalytics() =
    runBlocking<Unit> {
      // Prepare
      val mockTrackerService = mock(DatabaseInspectorAnalyticsTracker::class.java)
      project.registerServiceInstance(
        DatabaseInspectorAnalyticsTracker::class.java,
        mockTrackerService,
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

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
        mockTrackerService,
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

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
        mockTrackerService,
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

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
        mockTrackerService,
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
      whenever(mockMessenger.sendRawCommand(any())).thenReturn(cursor.toByteArray())

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
      taskExecutor,
    )
  }
}
