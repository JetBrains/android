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
package com.android.tools.idea.sqlite.repository

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.mocks.FakeSqliteResultSet
import com.android.tools.idea.sqlite.model.RowIdName
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteColumnValue
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.ui.tableView.OrderBy
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.android.tools.idea.testing.runDispatching
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import java.util.concurrent.Executor
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class DatabaseRepositoryTest : LightPlatformTestCase() {

  private lateinit var databaseRepository: DatabaseRepositoryImpl
  private lateinit var workerThread: Executor

  private lateinit var databaseConnection1: DatabaseConnection
  private lateinit var databaseConnection2: DatabaseConnection
  private lateinit var databaseConnection3: DatabaseConnection

  private val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
  private val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

  override fun setUp() {
    super.setUp()

    workerThread = PooledThreadExecutor.INSTANCE
    databaseRepository = DatabaseRepositoryImpl(project, workerThread)

    databaseConnection1 = mock()
    databaseConnection2 = mock()
    databaseConnection3 = mock()

    whenever(databaseConnection1.query(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    whenever(databaseConnection2.query(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))
    whenever(databaseConnection3.query(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(FakeSqliteResultSet()))

    whenever(databaseConnection1.execute(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(Unit))
    whenever(databaseConnection2.execute(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(Unit))
    whenever(databaseConnection3.execute(any(SqliteStatement::class.java)))
      .thenReturn(Futures.immediateFuture(Unit))

    whenever(databaseConnection1.readSchema())
      .thenReturn(
        Futures.immediateFuture(SqliteSchema(listOf(SqliteTable("t1", emptyList(), null, false))))
      )
    whenever(databaseConnection2.readSchema())
      .thenReturn(
        Futures.immediateFuture(SqliteSchema(listOf(SqliteTable("t2", emptyList(), null, false))))
      )
    whenever(databaseConnection3.readSchema())
      .thenReturn(
        Futures.immediateFuture(SqliteSchema(listOf(SqliteTable("t3", emptyList(), null, false))))
      )
  }

  fun testQueryDatabase() {
    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    val future1 =
      databaseRepository.runQuery(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
      )
    val future2 =
      databaseRepository.runQuery(
        databaseId2,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2")
      )

    pumpEventsAndWaitForFuture(future1)
    pumpEventsAndWaitForFuture(future2)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection3) }

    val future3 =
      databaseRepository.runQuery(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")
      )

    pumpEventsAndWaitForFuture(future3)

    // Assert
    verify(databaseConnection1)
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
    verify(databaseConnection2)
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2"))
    verify(databaseConnection1, times(0))
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
    verify(databaseConnection3)
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
  }

  fun testClosedDatabasesAreNotQueried() {
    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    val future1 =
      databaseRepository.runQuery(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
      )
    val future2 =
      databaseRepository.runQuery(
        databaseId2,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2")
      )

    pumpEventsAndWaitForFuture(future1)
    pumpEventsAndWaitForFuture(future2)

    runDispatching { databaseRepository.closeDatabase(databaseId1) }
    val future3 =
      databaseRepository.runQuery(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")
      )

    pumpEventsAndWaitForFutureException(future3)

    // Assert
    verify(databaseConnection1)
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
    verify(databaseConnection2)
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2"))
    verify(databaseConnection1, times(0))
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
  }

  fun testClosedDatabasesAreDisposed() {
    // Act
    Disposer.register(testRootDisposable, databaseConnection1)
    Disposer.register(testRootDisposable, databaseConnection2)
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    runDispatching { databaseRepository.closeDatabase(databaseId1) }
    runDispatching { databaseRepository.closeDatabase(databaseId2) }

    // Assert
    assertTrue(Disposer.isDisposed(databaseConnection1))
    assertTrue(Disposer.isDisposed(databaseConnection2))
  }

  fun testFetchSchema() {
    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    val schema1 = runDispatching { databaseRepository.fetchSchema(databaseId1) }
    val schema2 = runDispatching { databaseRepository.fetchSchema(databaseId2) }

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection3) }
    val schema3 = runDispatching { databaseRepository.fetchSchema(databaseId1) }

    // Assert
    assertEquals(schema1, SqliteSchema(listOf(SqliteTable("t1", emptyList(), null, false))))
    assertEquals(schema2, SqliteSchema(listOf(SqliteTable("t2", emptyList(), null, false))))
    assertEquals(schema3, SqliteSchema(listOf(SqliteTable("t3", emptyList(), null, false))))
  }

  fun testExecuteStatement() {
    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    val future1 =
      databaseRepository.executeStatement(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1")
      )
    val future2 =
      databaseRepository.executeStatement(
        databaseId2,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2")
      )

    pumpEventsAndWaitForFuture(future1)
    pumpEventsAndWaitForFuture(future2)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection3) }

    val future3 =
      databaseRepository.executeStatement(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")
      )
    pumpEventsAndWaitForFuture(future3)

    // Assert
    verify(databaseConnection1)
      .execute(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
    verify(databaseConnection2)
      .execute(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2"))
    verify(databaseConnection1, times(0))
      .execute(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
    verify(databaseConnection3)
      .execute(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
  }

  fun testUpdateTablePrimaryKey() {
    // Prepare
    val targetTable =
      SqliteTable(
        "t1",
        listOf(
          SqliteColumn("c1", SqliteAffinity.TEXT, false, true),
          SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
        ),
        null,
        false
      )
    val targetRow =
      SqliteRow(
        listOf(
          SqliteColumnValue("c1", SqliteValue.fromAny("0")),
          SqliteColumnValue("c2", SqliteValue.fromAny("oldC2"))
        )
      )
    val targetColumnName = "c2"
    val newValue = SqliteValue.fromAny("new")

    // Act
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    val future1 =
      databaseRepository.updateTable(
        databaseId1,
        targetTable,
        targetRow,
        targetColumnName,
        newValue
      )
    pumpEventsAndWaitForFuture(future1)

    // Assert
    verify(databaseConnection1)
      .execute(
        SqliteStatement(
          SqliteStatementType.UPDATE,
          "UPDATE t1 SET c2 = ? WHERE c1 = ?",
          listOf("new", "0").toSqliteValues(),
          "UPDATE t1 SET c2 = 'new' WHERE c1 = '0'"
        )
      )
  }

  fun testUpdateTableRowId() {
    // Prepare
    val targetTable =
      SqliteTable(
        "t1",
        listOf(
          SqliteColumn("c1", SqliteAffinity.TEXT, false, false),
          SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
        ),
        RowIdName.ROWID,
        false
      )
    val targetRow =
      SqliteRow(
        listOf(
          SqliteColumnValue("rowid", SqliteValue.fromAny("0")),
          SqliteColumnValue("c1", SqliteValue.fromAny("oldC1")),
          SqliteColumnValue("c2", SqliteValue.fromAny("oldC2"))
        )
      )
    val targetColumnName = "c2"
    val newValue = SqliteValue.fromAny("new")

    // Act
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    val future1 =
      databaseRepository.updateTable(
        databaseId1,
        targetTable,
        targetRow,
        targetColumnName,
        newValue
      )
    pumpEventsAndWaitForFuture(future1)

    // Assert
    verify(databaseConnection1)
      .execute(
        SqliteStatement(
          SqliteStatementType.UPDATE,
          "UPDATE t1 SET c2 = ? WHERE rowid = ?",
          listOf("new", "0").toSqliteValues(),
          "UPDATE t1 SET c2 = 'new' WHERE rowid = '0'"
        )
      )
  }

  fun testSelectOrderedAsc() {
    // Prepare
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    // Act
    val future =
      databaseRepository.selectOrdered(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        OrderBy.Asc("c1")
      )
    pumpEventsAndWaitForFuture(future)

    // Assert
    verify(databaseConnection1)
      .query(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "SELECT * FROM (SELECT * FROM t1) ORDER BY c1 ASC"
        )
      )
  }

  fun testSelectOrderedDesc() {
    // Prepare
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    // Act
    val future =
      databaseRepository.selectOrdered(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        OrderBy.Desc("c1")
      )
    pumpEventsAndWaitForFuture(future)

    // Assert
    verify(databaseConnection1)
      .query(
        SqliteStatement(
          SqliteStatementType.SELECT,
          "SELECT * FROM (SELECT * FROM t1) ORDER BY c1 DESC"
        )
      )
  }

  fun testSelectOrderedNotOrder() {
    // Prepare
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    // Act
    val future =
      databaseRepository.selectOrdered(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        OrderBy.NotOrdered
      )
    pumpEventsAndWaitForFuture(future)

    // Assert
    verify(databaseConnection1)
      .query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
  }

  fun testClear() {
    // Prepare
    Disposer.register(testRootDisposable, databaseConnection1)
    Disposer.register(testRootDisposable, databaseConnection2)
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }
    runDispatching { databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2) }

    // Act
    runDispatching { databaseRepository.clear() }

    // Assert
    verify(databaseConnection1).close()
    verify(databaseConnection2).close()

    assertTrue(Disposer.isDisposed(databaseConnection1))
    assertTrue(Disposer.isDisposed(databaseConnection2))

    pumpEventsAndWaitForFutureException(
      databaseRepository.runQuery(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")
      )
    )
    pumpEventsAndWaitForFutureException(
      databaseRepository.runQuery(
        databaseId2,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")
      )
    )
  }
}
