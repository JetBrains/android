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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
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
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.EdtExecutorService
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.Executor

class DatabaseRepositoryTest : LightPlatformTestCase() {

  private lateinit var databaseRepository: DatabaseRepositoryImpl
  private lateinit var uiThread: Executor

  override fun setUp() {
    super.setUp()

    uiThread = EdtExecutorService.getInstance()
    databaseRepository = DatabaseRepositoryImpl(project, uiThread)
  }

  fun testQueryDatabase() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseConnection2 = mock<DatabaseConnection>()
    val databaseConnection3 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    runDispatching { databaseRepository.runQuery(databaseId1, SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1")) }
    runDispatching { databaseRepository.runQuery(databaseId2, SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t2")) }

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection3)
    }
    runDispatching {
      databaseRepository.runQuery(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
    }

    // Assert
    verify(databaseConnection1).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1"))
    verify(databaseConnection2).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t2"))
    verify(databaseConnection1, times(0)).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t3"))
    verify(databaseConnection3).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t3"))
  }

  fun testClosedDatabasesAreNotQueried() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseConnection2 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    runDispatching { databaseRepository.runQuery(databaseId1, SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1")) }
    runDispatching { databaseRepository.runQuery(databaseId2, SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t2")) }

    runDispatching {
      databaseRepository.closeDatabase(databaseId1)
    }
    pumpEventsAndWaitForFutureException(databaseRepository.runQuery(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")))

    // Assert
    verify(databaseConnection1).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1"))
    verify(databaseConnection2).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t2"))
    verify(databaseConnection1, times(0)).query(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t3"))
  }

  fun testFetchSchema() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    `when`(databaseConnection1.readSchema())
      .thenReturn(Futures.immediateFuture(SqliteSchema(listOf(SqliteTable("t1", emptyList(), null, false)))))
    val databaseConnection2 = mock<DatabaseConnection>()
    `when`(databaseConnection2.readSchema())
      .thenReturn(Futures.immediateFuture(SqliteSchema(listOf(SqliteTable("t2", emptyList(), null, false)))))
    val databaseConnection3 = mock<DatabaseConnection>()
    `when`(databaseConnection3.readSchema())
      .thenReturn(Futures.immediateFuture(SqliteSchema(listOf(SqliteTable("t3", emptyList(), null, false)))))
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

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
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseConnection2 = mock<DatabaseConnection>()
    val databaseConnection3 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)

    // Act
    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1)
      databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2)
    }

    runDispatching {
      databaseRepository.executeStatement(databaseId1, SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1"))
    }
    runDispatching {
      databaseRepository.executeStatement(databaseId2, SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t2"))
    }

    runDispatching {
      databaseRepository.addDatabaseConnection(databaseId1, databaseConnection3)
    }
    runDispatching {
      databaseRepository.executeStatement(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3"))
    }

    // Assert
    verify(databaseConnection1).execute(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1"))
    verify(databaseConnection2).execute(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t2"))
    verify(databaseConnection1, times(0)).execute(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t3"))
    verify(databaseConnection3).execute(SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t3"))
  }

  fun testUpdateTablePrimaryKey() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    val targetTable = SqliteTable("t1", listOf(
      SqliteColumn("c1", SqliteAffinity.TEXT, false, true),
      SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    ), null, false)
    val targetRow = SqliteRow(listOf(
      SqliteColumnValue("c1", SqliteValue.fromAny("0")),
      SqliteColumnValue("c2", SqliteValue.fromAny("oldC2"))
    ))
    val targetColumnName = "c2"
    val newValue = SqliteValue.fromAny("new")

    // Act
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    runDispatching {
      databaseRepository.updateTable(databaseId1, targetTable, targetRow, targetColumnName, newValue)
    }

    // Assert
    verify(databaseConnection1).execute(
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
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    val targetTable = SqliteTable("t1", listOf(
      SqliteColumn("c1", SqliteAffinity.TEXT, false, false),
      SqliteColumn("c2", SqliteAffinity.TEXT, false, false)
    ), RowIdName.ROWID, false)
    val targetRow = SqliteRow(listOf(
      SqliteColumnValue("rowid", SqliteValue.fromAny("0")),
      SqliteColumnValue("c1", SqliteValue.fromAny("oldC1")),
      SqliteColumnValue("c2", SqliteValue.fromAny("oldC2"))
    ))
    val targetColumnName = "c2"
    val newValue = SqliteValue.fromAny("new")

    // Act
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    runDispatching {
      databaseRepository.updateTable(databaseId1, targetTable, targetRow, targetColumnName, newValue)
    }

    // Assert
    verify(databaseConnection1).execute(
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
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    // Act
    runDispatching {
      databaseRepository.selectOrdered(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        OrderBy.Asc("c1")
      )
    }

    // Assert
    verify(databaseConnection1).query(
      SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM (SELECT * FROM t1) ORDER BY c1 ASC")
    )
  }

  fun testSelectOrderedDesc() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    // Act
    runDispatching {
      databaseRepository.selectOrdered(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        OrderBy.Desc("c1")
      )
    }

    // Assert
    verify(databaseConnection1).query(
      SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM (SELECT * FROM t1) ORDER BY c1 DESC")
    )
  }

  fun testSelectOrderedNotOrder() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)

    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }

    // Act
    runDispatching {
      databaseRepository.selectOrdered(
        databaseId1,
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"),
        OrderBy.NotOrdered
      )
    }

    // Assert
    verify(databaseConnection1).query(
      SqliteStatement(SqliteStatementType.SELECT,"SELECT * FROM t1")
    )
  }

  fun testRelease() {
    // Prepare
    val databaseConnection1 = mock<DatabaseConnection>()
    val databaseConnection2 = mock<DatabaseConnection>()
    val databaseId1 = SqliteDatabaseId.fromLiveDatabase("db1", 1)
    val databaseId2 = SqliteDatabaseId.fromLiveDatabase("db2", 2)
    runDispatching { databaseRepository.addDatabaseConnection(databaseId1, databaseConnection1) }
    runDispatching { databaseRepository.addDatabaseConnection(databaseId2, databaseConnection2) }

    // Act
    runDispatching { databaseRepository.release() }

    // Assert
    verify(databaseConnection1).close()
    verify(databaseConnection2).close()

    pumpEventsAndWaitForFutureException(databaseRepository.runQuery(databaseId1, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")))
    pumpEventsAndWaitForFutureException(databaseRepository.runQuery(databaseId2, SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t3")))
  }
}