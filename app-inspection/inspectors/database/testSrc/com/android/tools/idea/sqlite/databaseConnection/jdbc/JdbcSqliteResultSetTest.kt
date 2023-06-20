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
package com.android.tools.idea.sqlite.databaseConnection.jdbc

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureCancellation
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.concurrency.EdtExecutorService

class JdbcSqliteResultSetTest : LightPlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private var customConnection: DatabaseConnection? = null

  override fun setUp() {
    super.setUp()
    sqliteUtil =
      SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()
  }

  override fun tearDown() {
    try {
      if (customConnection != null) {
        pumpEventsAndWaitForFuture(customConnection!!.close())
      }

      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun `test CreateResultSet ThenAddColumnToTable ResultSetReturnsCorrectListOfColumns`() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (42)"
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    val columnsBeforeAlterTable = pumpEventsAndWaitForFuture(resultSet.columns)

    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD COLUMN c2 INT")
      )
    )
    val columnsAfterAlterTable = pumpEventsAndWaitForFuture(resultSet.columns)

    // Assert
    assertSize(1, columnsBeforeAlterTable)
    assertSize(2, columnsAfterAlterTable)
    assertEquals("c1", columnsBeforeAlterTable.first().name)
    assertEquals("c1", columnsAfterAlterTable[0].name)
    assertEquals("c2", columnsAfterAlterTable[1].name)
  }

  fun `test CreateResultSet ThenAddRowToTable ResultSetReturnsCorrectNumberOfRows`() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (1)"
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    val rowCountBeforeInsert = pumpEventsAndWaitForFuture(resultSet.totalRowCount)

    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.INSERT, "INSERT INTO t1 (c1) VALUES (2)")
      )
    )
    val rowCountAfterInsert = pumpEventsAndWaitForFuture(resultSet.totalRowCount)

    // Assert
    assertEquals(1, rowCountBeforeInsert)
    assertEquals(2, rowCountAfterInsert)
  }

  fun `test CreateResultSetFromExplain ThenAddColumn ResultSetReturnsOneMoreRow`() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (1)"
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(
          SqliteStatement(SqliteStatementType.EXPLAIN, "EXPLAIN SELECT * FROM t1")
        )
      )
    val rowCountBefore = pumpEventsAndWaitForFuture(resultSet.totalRowCount)

    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "ALTER TABLE t1 ADD COLUMN c2 text")
      )
    )
    val rowCountAfter = pumpEventsAndWaitForFuture(resultSet.totalRowCount)

    // Assert
    assertEquals(9, rowCountBefore)
    assertEquals(10, rowCountAfter)
  }

  fun testDisposeCancelsGetColumns() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (42)"
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    Disposer.dispose(customConnection!!)
    pumpEventsAndWaitForFutureCancellation(resultSet.columns)
  }

  fun testDisposeCancelsGetRowCount() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (42)"
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    Disposer.dispose(customConnection!!)
    pumpEventsAndWaitForFutureCancellation(resultSet.totalRowCount)
  }

  fun testDisposeCancelsGetRowBatch() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        createStatement = "CREATE TABLE t1 (c1 INT)",
        insertStatement = "INSERT INTO t1 (c1) VALUES (42)"
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    Disposer.dispose(customConnection!!)
    pumpEventsAndWaitForFutureCancellation(resultSet.getRowBatch(0, 10))
  }
}
