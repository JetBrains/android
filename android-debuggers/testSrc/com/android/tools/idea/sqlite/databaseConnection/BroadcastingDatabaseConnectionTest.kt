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
package com.android.tools.idea.sqlite.databaseConnection

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class BroadcastingDatabaseConnectionTest : LightPlatformTestCase() {

  private lateinit var taskExecutor: FutureCallbackExecutor
  private lateinit var mockInternalDatabaseConnection: DatabaseConnection
  private lateinit var databaseConnection: DatabaseConnection

  override fun setUp() {
    super.setUp()
    taskExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)
    mockInternalDatabaseConnection = mock(DatabaseConnection::class.java)
    databaseConnection = BroadcastingDatabaseConnection(mockInternalDatabaseConnection, taskExecutor)
  }

  fun testClose() {
    // Act
    databaseConnection.close()

    // Assert
    verify(mockInternalDatabaseConnection).close()
  }

  fun testReadSchema() {
    // Act
    databaseConnection.readSchema()

    verify(mockInternalDatabaseConnection).readSchema()
  }

  fun testExecuteSuccess() {
    // Prepare
    val mockDatabaseConnectionListener = mock(DatabaseConnectionListener::class.java)
    ApplicationManager.getApplication().messageBus.connect().subscribe(DatabaseConnection.TOPIC, mockDatabaseConnectionListener)

    `when`(mockInternalDatabaseConnection
             .execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFuture(mock(SqliteResultSet::class.java)))

    // Act
    pumpEventsAndWaitForFuture(databaseConnection.execute(SqliteStatement("select * from t")))

    // Assert
    verify(mockDatabaseConnectionListener).onSqliteStatementExecutionSuccess(SqliteStatement("select * from t"))
  }

  fun testExecuteFailure() {
    // Prepare
    val mockDatabaseConnectionListener = mock(DatabaseConnectionListener::class.java)
    ApplicationManager.getApplication().messageBus.connect().subscribe(DatabaseConnection.TOPIC, mockDatabaseConnectionListener)

    `when`(mockInternalDatabaseConnection
             .execute(any(SqliteStatement::class.java))).thenReturn(Futures.immediateFailedFuture(RuntimeException()))

    // Act
    pumpEventsAndWaitForFutureException(databaseConnection.execute(SqliteStatement("select * from t")))

    // Assert
    verify(mockDatabaseConnectionListener).onSqliteStatementExecutionFailed(SqliteStatement("select * from t"))
  }
}