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

import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.utils.SqliteTestUtil
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.toSqliteValues
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor

class JdbcDatabaseConnectionTest : LightPlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private lateinit var sqliteFile: VirtualFile
  private var customSqliteFile: VirtualFile? = null
  private lateinit var databaseConnection: DatabaseConnection
  private var customConnection: DatabaseConnection? = null
  private var previouslyEnabled: Boolean = false

  override fun setUp() {
    super.setUp()
    sqliteUtil =
      SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()

    sqliteFile = sqliteUtil.createTestSqliteDatabase()
    databaseConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          sqliteFile,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )
  }

  override fun tearDown() {
    try {
      pumpEventsAndWaitForFuture(databaseConnection.close())
      if (customConnection != null) {
        pumpEventsAndWaitForFuture(customConnection!!.close())
      }

      sqliteUtil.tearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testReadSchemaFailsIfDatabaseNotOpened() {
    // Prepare
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
    assertThat(authorTable?.hasColumn("author_id", SqliteAffinity.INTEGER)).isTrue()
    assertThat(authorTable?.hasColumn("first_name", SqliteAffinity.TEXT)).isTrue()
    assertThat(authorTable?.hasColumn("last_name", SqliteAffinity.TEXT)).isTrue()

    val bookTable = schema.tables.find { it.name == "Book" }
    assertThat(bookTable).isNotNull()
    assertThat(bookTable?.hasColumn("book_id", SqliteAffinity.INTEGER)).isTrue()
    assertThat(bookTable?.hasColumn("title", SqliteAffinity.TEXT)).isTrue()
    assertThat(bookTable?.hasColumn("isbn", SqliteAffinity.TEXT)).isTrue()
    assertThat(bookTable?.hasColumn("author_id", SqliteAffinity.INTEGER)).isTrue()
  }

  fun testCloseUnlocksFile() {
    // Prepare

    // Act
    pumpEventsAndWaitForFuture(databaseConnection.close())
    ApplicationManager.getApplication().runWriteAction { sqliteFile.delete(this) }

    // Assert
    assertThat(sqliteFile.exists()).isFalse()
  }

  fun testExecuteQuerySelectAllReturnsResultSet() {
    // Prepare

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        databaseConnection.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM Book"))
      )!!

    // Assert
    assertThat(resultSet.hasColumn("book_id", SqliteAffinity.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", SqliteAffinity.TEXT)).isTrue()
    assertThat(resultSet.hasColumn("isbn", SqliteAffinity.TEXT)).isTrue()
    assertThat(resultSet.hasColumn("author_id", SqliteAffinity.INTEGER)).isTrue()

    // Act
    var rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 3)).rows

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1)).rows

    // Assert
    assertThat(rows.count()).isEqualTo(1)
  }

  fun testExecuteQuerySelectColumnReturnsResultSet() {
    // Prepare

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        databaseConnection.query(
          SqliteStatement(SqliteStatementType.SELECT, "SELECT book_id FROM Book")
        )
      )!!

    // Assert
    assertThat(resultSet.hasColumn("book_id", SqliteAffinity.INTEGER)).isTrue()
    assertThat(resultSet.hasColumn("title", SqliteAffinity.TEXT)).isFalse()
    assertThat(resultSet.hasColumn("isbn", SqliteAffinity.TEXT)).isFalse()
    assertThat(resultSet.hasColumn("author_id", SqliteAffinity.INTEGER)).isFalse()

    // Act
    var rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 3)).rows

    // Assert
    assertThat(rows.count()).isEqualTo(3)

    // Act
    rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 1)).rows

    // Assert
    assertThat(rows.count()).isEqualTo(1)
  }

  fun testExecuteUpdateDropTable() {
    // Prepare

    // Act/Assert
    pumpEventsAndWaitForFuture(
      databaseConnection.execute(SqliteStatement(SqliteStatementType.UNKNOWN, "DROP TABLE Book"))
    )
    pumpEventsAndWaitForFutureException(
      databaseConnection.execute(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM Book"))
    )
  }

  fun testExecuteQueryFailsWhenIncorrectTableName() {
    // Prepare

    // Act/Assert
    pumpEventsAndWaitForFutureException(
      databaseConnection.execute(
        SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM wrongName")
      )
    )
  }

  fun test_rowid_IsAssignedCorrectly() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1", "col2"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.first().rowIdName!!.stringName).isEqualTo("_rowid_")
  }

  fun testRowidIsAssignedCorrectly() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("rowidDb", "testTable", listOf("col1", "col2", "_rowid_"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.first().rowIdName!!.stringName).isEqualTo("rowid")
  }

  fun testOidIsAssignedCorrectly() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "rowidDb",
        "testTable",
        listOf("col1", "col2", "_rowid_", "rowid"),
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.first().rowIdName!!.stringName).isEqualTo("oid")
  }

  fun testRowIdIsNull() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "rowidDb",
        "testTable",
        listOf("col1", "col2", "rowid", "oid", "_rowid_"),
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.first().rowIdName).isNull()
  }

  fun testPrimaryKeyInWithoutRowIdTable() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "rowidDb",
        "testTable",
        listOf("col1"),
        listOf("pk"),
        true,
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.first().rowIdName).isNull()
    val pk = schema.tables.first().columns.find { it.name == "pk" }
    assertThat(pk!!.inPrimaryKey).isTrue()
  }

  fun testMultiplePrimaryKeys() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "rowidDb",
        "testTable",
        listOf("col1"),
        listOf("pk1", "pk2"),
        false,
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    val pk1 = schema.tables.first().columns.find { it.name == "pk1" }
    assertThat(pk1!!.inPrimaryKey).isTrue()
    val pk2 = schema.tables.first().columns.find { it.name == "pk2" }
    assertThat(pk2!!.inPrimaryKey).isTrue()
  }

  fun testAffinity() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabaseWithConfigurableTypes(
        "affinityDb",
        "testTable",
        listOf("int", "text", "blob", "real", "numeric"),
      )

    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    val columns = schema.tables.first().columns
    assertThat(columns.first { it.name == "column0" }.affinity).isEqualTo(SqliteAffinity.INTEGER)
    assertThat(columns.first { it.name == "column1" }.affinity).isEqualTo(SqliteAffinity.TEXT)
    assertThat(columns.first { it.name == "column2" }.affinity).isEqualTo(SqliteAffinity.BLOB)
    assertThat(columns.first { it.name == "column3" }.affinity).isEqualTo(SqliteAffinity.REAL)
    assertThat(columns.first { it.name == "column4" }.affinity).isEqualTo(SqliteAffinity.NUMERIC)
  }

  fun testNotNull() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createTestSqliteDatabase(
        "rowidDb",
        "testTable",
        listOf("col1"),
        listOf("pk"),
        true,
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    val pk = schema.tables.first().columns.find { it.name == "pk" }
    val col1 = schema.tables.first().columns.find { it.name == "col1" }
    assertThat(pk!!.isNullable).isFalse()
    assertThat(col1!!.isNullable).isTrue()
  }

  fun testReadSchemaTabNameRequiresEscaping() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "table''Name", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "table''Name" }!!
    assertThat(table.columns).hasSize(1)
    assertThat(table.columns.first().name).isEqualTo("c1")
  }

  fun testReadSchemaTabNameRequiresEscaping1() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "table'Name", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "table'Name" }!!
    assertThat(table.columns.map { it.name }).containsExactly("c1")
  }

  fun testReadSchemaTabNameRequiresEscaping2() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "table`Name", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "table`Name" }!!
    assertThat(table.columns.map { it.name }).containsExactly("c1")
  }

  fun testReadSchemaTabNameRequiresEscaping3() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "table\'Name", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "table\'Name" }!!
    assertThat(table.columns.map { it.name }).containsExactly("c1")
  }

  fun testReadSchemaTabNameRequiresEscaping4() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "table\"Name", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "table\"Name" }!!
    assertThat(table.columns.map { it.name }).containsExactly("c1")
  }

  fun testReadSchemaTabNameRequiresEscaping5() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "table Name", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "table Name" }!!
    assertThat(table.columns.map { it.name }).containsExactly("c1")
  }

  fun testReadSchemaColNameRequiresEscaping() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("col''Name"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "tableName" }!!
    assertThat(table.columns.map { it.name }).containsExactly("col''Name")
  }

  fun testReadSchemaColNameRequiresEscaping1() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("col'Name"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "tableName" }!!
    assertThat(table.columns.map { it.name }).containsExactly("col'Name")
  }

  fun testReadSchemaColNameRequiresEscaping2() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("col`Name"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "tableName" }!!
    assertThat(table.columns.map { it.name }).containsExactly("col`Name")
  }

  fun testReadSchemaColNameRequiresEscaping3() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("col\'Name"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "tableName" }!!
    assertThat(table.columns.map { it.name }).containsExactly("col'Name")
  }

  fun testReadSchemaColNameRequiresEscaping4() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("col\"Name"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "tableName" }!!
    assertThat(table.columns.map { it.name }).containsExactly("col\"Name")
  }

  fun testReadSchemaColNameRequiresEscaping5() {
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("col Name"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    val schema = pumpEventsAndWaitForFuture(customConnection!!.readSchema())

    // Assert
    assertThat(schema.tables.count()).isEqualTo(1)
    val table = schema.tables.find { it.name == "tableName" }!!
    assertThat(table.columns.map { it.name }).containsExactly("col Name")
  }

  fun testInsertNullValueWorks() {
    // Prepare
    val customSqliteFile =
      sqliteUtil.createTestSqliteDatabase("customDb", "tableName", listOf("c1"))
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile,
          FutureCallbackExecutor.wrap(EdtExecutorService.getInstance()),
        )
      )

    // Act
    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "CREATE TABLE t1 (c1 text, c2 text)")
      )
    )

    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(
          SqliteStatementType.INSERT,
          "INSERT INTO t1 (c1, c2) VALUES (?, ?)",
          listOf(null, "null").toSqliteValues(),
          "INSERT INTO t1 (c1, c2) VALUES (null, 'null')",
        )
      )
    )

    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )

    // Assert
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows.first().values[0].value).isEqualTo(SqliteValue.NullValue)
    assertThat(rows.first().values[1].value).isEqualTo(SqliteValue.StringValue("null"))
  }

  fun testUpdateStatement() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        "db",
        "create table t1 (c1 int)",
        "insert into t1 values (42)",
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.UPDATE, "UPDATE t1 SET c1 = 0 WHERE c1 == 42")
      )
    )

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows.first().values.first().value).isEqualTo(SqliteValue.fromAny(0))
  }

  fun testInsertStatement() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        "db",
        "create table t1 (c1 int)",
        "insert into t1 values (42)",
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.INSERT, "insert into t1 values (0)")
      )
    )

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t1"))
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows.last().values.first().value).isEqualTo(SqliteValue.fromAny(0))
  }

  fun testCreateTable() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        "db",
        "create table t1 (c1 int)",
        "insert into t1 values (42)",
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    pumpEventsAndWaitForFuture(
      customConnection!!.execute(
        SqliteStatement(SqliteStatementType.UNKNOWN, "create table t2 (c1 int)")
      )
    )

    // Assert
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(SqliteStatement(SqliteStatementType.SELECT, "SELECT * FROM t2"))
      )
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).isEmpty()
  }

  fun testExplainStatement() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        "db",
        "create table t1 (c1 int)",
        "insert into t1 values (42)",
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(
          SqliteStatement(SqliteStatementType.EXPLAIN, "explain select * from t1")
        )
      )

    // Assert
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).isNotEmpty()
  }

  fun testPragmaStatement() {
    // Prepare
    customSqliteFile =
      sqliteUtil.createAdHocSqliteDatabase(
        "db",
        "create table t1 (c1 int)",
        "insert into t1 values (42)",
      )
    customConnection =
      pumpEventsAndWaitForFuture(
        getJdbcDatabaseConnection(
          testRootDisposable,
          customSqliteFile!!,
          FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE),
        )
      )

    // Act
    val resultSet =
      pumpEventsAndWaitForFuture(
        customConnection!!.query(
          SqliteStatement(SqliteStatementType.PRAGMA_QUERY, "PRAGMA cache_size")
        )
      )

    // Assert
    val rows = pumpEventsAndWaitForFuture(resultSet.getRowBatch(0, 10)).rows
    assertThat(rows).isNotEmpty()
  }

  private fun SqliteResultSet.hasColumn(name: String, affinity: SqliteAffinity): Boolean {
    return pumpEventsAndWaitForFuture(this.columns)
      .find { it.name == name }
      ?.affinity
      ?.equals(affinity) ?: false
  }

  private fun SqliteTable.hasColumn(name: String, affinity: SqliteAffinity): Boolean {
    return this.columns.find { it.name == name }?.affinity?.equals(affinity) ?: false
  }
}
