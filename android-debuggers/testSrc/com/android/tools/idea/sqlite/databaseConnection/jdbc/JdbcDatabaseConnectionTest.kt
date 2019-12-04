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
package com.android.tools.idea.sqlite.databaseConnection.jdbc

import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.DatabaseInspectorFlagController
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.fileType.SqliteTestUtil
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.ide.PooledThreadExecutor
import java.sql.DriverManager
import java.sql.JDBCType

class JdbcDatabaseConnectionTest : PlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var sqliteFile: VirtualFile
  private var customSqliteFile: VirtualFile? = null
  private lateinit var databaseConnection: DatabaseConnection
  private var customConnection: DatabaseConnection? = null
  private var previouslyEnabled: Boolean = false

  override fun setUp() {
    super.setUp()
    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()
    previouslyEnabled = DatabaseInspectorFlagController.enableFeature(true)

    sqliteFile = sqliteUtil.createTestSqliteDatabase()
    databaseConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(sqliteFile, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )
  }

  override fun tearDown() {
    try {
      pumpEventsAndWaitForFuture(databaseConnection.close())
      if (customConnection != null) {
        pumpEventsAndWaitForFuture(customConnection!!.close())
      }

      sqliteUtil.tearDown()
      DatabaseInspectorFlagController.enableFeature(previouslyEnabled)
    }
    finally {
      super.tearDown()
    }
  }

  fun testReadSchemaFailsIfDatabaseNotOpened() {
    //Prepare
    pumpEventsAndWaitForFuture(databaseConnection.close())

    // Act
    val error = pumpEventsAndWaitForFutureException(databaseConnection.readSchema())

    // Assert
    assertThat(error).isNotNull()
  }

  fun testReadSchemaReturnsTablesAndColumns() {
    // Prepare

    // Act
    val schema = pumpEventsAndWaitForFuture(databaseConnection.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(2)
    val authorTable = schema.tables.find { it.name == "Author" }
    assertThat(authorTable).isNotNull()
    assertThat(authorTable?.columns?.count()).isEqualTo(3)
    assertThat(authorTable?.hasColumn("author_id", JDBCType.INTEGER)).isTrue()
    assertThat(authorTable?.hasColumn("first_name", JDBCType.VARCHAR)).isTrue()
    assertThat(authorTable?.hasColumn("last_name", JDBCType.VARCHAR)).isTrue()

    val bookTable = schema.tables.find { it.name == "Book" }
    assertThat(bookTable).isNotNull()
    assertThat(bookTable?.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(bookTable?.hasColumn("title", JDBCType.VARCHAR)).isTrue()
    assertThat(bookTable?.hasColumn("isbn", JDBCType.VARCHAR)).isTrue()
    assertThat(bookTable?.hasColumn("author_id", JDBCType.INTEGER)).isTrue()
  }

  fun testCloseUnlocksFile() {
    // Prepare

    // Act
    pumpEventsAndWaitForFuture(databaseConnection.close())
    ApplicationManager.getApplication().runWriteAction {
      sqliteFile.delete(this)
    }

    // Assert
    assertThat(sqliteFile.exists()).isFalse()
  }

  fun testExecuteQuerySelectAllReturnsResultSet() {
    // Prepare

    // Act
    val resultSet = pumpEventsAndWaitForFuture(databaseConnection.execute(SqliteStatement("SELECT * FROM Book")))!!

    // Assert
    assertThat(resultSet.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", JDBCType.VARCHAR)).isTrue()
    assertThat(resultSet.hasColumn("isbn", JDBCType.VARCHAR)).isTrue()
    assertThat(resultSet.hasColumn("author_id", JDBCType.INTEGER)).isTrue()

    // Act
    var rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 3))

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0,1))

    // Assert
    assertThat(rows.count()).isEqualTo(1)
  }

  fun testExecuteQuerySelectColumnReturnsResultSet() {
    // Prepare

    // Act
    val resultSet = pumpEventsAndWaitForFuture(databaseConnection.execute(SqliteStatement("SELECT book_id FROM Book")))!!

    // Assert
    assertThat(resultSet.hasColumn("book_id", JDBCType.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", JDBCType.VARCHAR)).isFalse()
    assertThat(resultSet.hasColumn("isbn", JDBCType.VARCHAR)).isFalse()
    assertThat(resultSet.hasColumn("author_id", JDBCType.INTEGER)).isFalse()

    // Act
    var rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0,3))

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0,1))

    // Assert
    assertThat(rows.count()).isEqualTo(1)
  }

  fun testExecuteUpdateDropTable() {
    // Prepare

    // Act/Assert
    pumpEventsAndWaitForFuture(databaseConnection.execute(SqliteStatement("DROP TABLE Book")))
    pumpEventsAndWaitForFutureException(databaseConnection.execute(SqliteStatement("SELECT * FROM Book")))
  }

  fun testResultSetThrowsAfterDisposed() {
    // Prepare

    // Act
    val resultSet = pumpEventsAndWaitForFuture(databaseConnection.execute(SqliteStatement("SELECT * FROM Book")))!!
    Disposer.dispose(resultSet)
    val error = pumpEventsAndWaitForFutureException(resultSet.getRowBatch(0,3))

    // Assert
    assertThat(error).isNotNull()
  }

  fun testExecuteQueryFailsWhenIncorrectTableName() {
    // Prepare

    // Act/Assert
    pumpEventsAndWaitForFutureException(
      databaseConnection.execute(SqliteStatement("SELECT * FROM wrongName"))
    )
  }

  fun test_rowid_IsAssignedCorrectly() {
    // Prepare
    customSqliteFile = sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1", "col2"))
    customConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(customSqliteFile!!, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertEquals("_rowid_", schema.tables.first().rowIdName!!.stringName)
  }

  fun testRowidIsAssignedCorrectly() {
    // Prepare
    customSqliteFile = sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1", "col2", "_rowid_"))
    customConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(customSqliteFile!!, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertEquals("rowid", schema.tables.first ().rowIdName!!.stringName)
  }

  fun testOidIsAssignedCorrectly() {
    // Prepare
    customSqliteFile = sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1", "col2", "_rowid_", "rowid"))
    customConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(customSqliteFile!!, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertEquals("oid", schema.tables.first ().rowIdName!!.stringName)
  }

  fun testRowIdIsNull() {
    // Prepare
    customSqliteFile = sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1", "col2", "rowid", "oid", "_rowid_"))
    customConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(customSqliteFile!!, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertNull("rowid", schema.tables.first().rowIdName)
  }

  fun testPrimaryKeyInWithoutRowIdTable() {
    // Prepare
    customSqliteFile = sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1"), listOf("pk"), true)
    customConnection = pumpEventsAndWaitForFuture(
      getSqliteJdbcService(customSqliteFile!!, FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE))
    )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertNull(schema.tables.first().rowIdName)
    val pk = schema.tables.first().columns.find { it.name == "pk" }
    assertTrue(pk!!.inPrimaryKey)
  }

  private fun SqliteResultSet.hasColumn(name: String, type: JDBCType) : Boolean {
    return pumpEventsAndWaitForFuture(this.columns).find { it.name == name }?.type?.equals(type) ?: false
  }

  private fun SqliteTable.hasColumn(name: String, type: JDBCType) : Boolean {
    return this.columns.find { it.name == name }?.type?.equals(type) ?: false
  }

  private fun getSqliteJdbcService(sqliteFile: VirtualFile, executor: FutureCallbackExecutor): ListenableFuture<DatabaseConnection> {
    return executor.executeAsync {
      val url = "jdbc:sqlite:${sqliteFile.path}"
      val connection = DriverManager.getConnection(url)

      return@executeAsync JdbcDatabaseConnection(connection, sqliteFile, executor)
    }
  }
}
