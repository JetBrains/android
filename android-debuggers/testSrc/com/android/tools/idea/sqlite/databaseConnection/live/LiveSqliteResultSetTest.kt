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
import com.android.testutils.MockitoKt
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertThrows
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito

class LiveSqliteResultSetTest : LightPlatformTestCase() {
  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)

  fun testColumnsReturnCorrectListOfColumns() {
    // Prepare
    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT"), mockMessenger, 0, taskExecutor)

    // Act
    val columnsFromResultSet = pumpEventsAndWaitForFuture(resultSet.columns)

    // Assert
    assertEquals(
      listOf(
        SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
        SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
      ),
      columnsFromResultSet
    )
  }

  fun testRowCountReturnsCorrectNumberOfRows() {
    // Prepare
    val rowCountCellValue = SqliteInspectorProtocol.CellValue.newBuilder()
      .setColumnName("COUNT(*)")
      .setIntValue(12345)
      .build()

    val row = SqliteInspectorProtocol.Row.newBuilder()
      .addValues(rowCountCellValue)
      .build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    Mockito.`when`(mockMessenger.sendRawCommand(MockitoKt.any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT"), mockMessenger, 0, taskExecutor)

    // Act
    val rowCount = pumpEventsAndWaitForFuture(resultSet.rowCount)

    // Assert
    assertEquals(12345, rowCount)
  }

  fun testRowCountFailsIfDisposed() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    Mockito.`when`(mockMessenger.sendRawCommand(MockitoKt.any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT COUNT(*) FROM (query)"), mockMessenger, 0, taskExecutor)
    Disposer.register(project, resultSet)

    // Act / Assert
    Disposer.dispose(resultSet)
    pumpEventsAndWaitForFutureException(resultSet.rowCount)
  }

  fun testGetRowBatchReturnsCorrectListOfRows() {
    // Prepare
    val cellValueString = SqliteInspectorProtocol.CellValue.newBuilder()
      .setColumnName("column1")
      .setStringValue("a string")
      .build()

    val row = SqliteInspectorProtocol.Row.newBuilder()
      .addValues(cellValueString)
      .addValues(cellValueString)
      .addValues(cellValueString)
      .build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    Mockito.`when`(mockMessenger.sendRawCommand(MockitoKt.any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT"), mockMessenger, 0, taskExecutor)

    // Act
    // Since we are mocking the answer the values passed to getRowBatch don't matter.
    val rowsFromResultSet = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, Integer.MAX_VALUE))

    // Assert
    assertSize(1, rowsFromResultSet)
    assertEquals("column1", rowsFromResultSet.first().values.first().column.name)
    assertEquals("a string", rowsFromResultSet.first().values.first().value)
  }

  fun testGetRowBatchFailsIfDisposed() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    Mockito.`when`(mockMessenger.sendRawCommand(MockitoKt.any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT"), mockMessenger, 0, taskExecutor)
    Disposer.register(project, resultSet)

    // Act / Assert
    Disposer.dispose(resultSet)
    pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0, Integer.MAX_VALUE))
  }

  fun testGetRowBatchThrowsIfMinOffsetSmallerThanZero() {
    // Prepare
    val row = SqliteInspectorProtocol.Row.newBuilder().build()

    val cursor = SqliteInspectorProtocol.Response.newBuilder()
      .setQuery(SqliteInspectorProtocol.QueryResponse.newBuilder().addRows(row))
      .build()

    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    Mockito.`when`(mockMessenger.sendRawCommand(MockitoKt.any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT"), mockMessenger, 0, taskExecutor)

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

    val mockMessenger = Mockito.mock(AppInspectorClient.CommandMessenger::class.java)
    Mockito.`when`(mockMessenger.sendRawCommand(MockitoKt.any(ByteArray::class.java)))
      .thenReturn(Futures.immediateFuture(cursor.toByteArray()))

    val columns = listOf(
      SqliteColumn("col1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("col2", SqliteAffinity.INTEGER, true, false)
    )
    val resultSet = LiveSqliteResultSet(columns, SqliteStatement("SELECT"), mockMessenger, 0, taskExecutor)

    // Act / Assert
    assertThrows<IllegalArgumentException>(IllegalArgumentException::class.java) {
      resultSet.getRowBatch(0, 0)
    }
  }
}