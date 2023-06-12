/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite.fileType

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.TempDirTestFixture
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

class SqliteTestUtil(private val tempDirTestFixture: TempDirTestFixture) {

  fun setUp() {
    tempDirTestFixture.setUp()
  }

  fun tearDown() {
    tempDirTestFixture.tearDown()
  }

  fun createByteSequence(file: VirtualFile, size: Int): ByteSequence = runReadAction {
    val bytes = ByteArray(size)
    file.inputStream.use { stream ->
      val length = stream.read(bytes)
      ByteArraySequence(bytes, 0, length)
    }
  }

  fun createTestSqliteDatabase(name: String = "sqlite-database"): VirtualFile = runWriteAction {
    createEmptyTempSqliteDatabase(name).also { file ->
      // Note: We need to close the connection so the database file handle is released by the Sqlite
      // engine.
      openSqliteDatabase(file).use(::fillTestDatabase)

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  // TODO(b/150800193) refactor to use createAdHocSqliteDatabase instead of this.
  fun createTestSqliteDatabase(
    dbName: String = "sqlite-database",
    tableName: String = "tab",
    columns: List<String> = emptyList(),
    primaryKeys: List<String> = emptyList(),
    withoutRowId: Boolean = false
  ): VirtualFile = runWriteAction {
    createEmptyTempSqliteDatabase(dbName).also { file ->
      // Note: We need to close the connection so the database file handle is released by the Sqlite
      // engine.
      openSqliteDatabase(file).use { connection ->
        fillConfigurableTestDB(connection, tableName, columns, primaryKeys, withoutRowId)
      }

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  fun createAdHocSqliteDatabase(
    dbName: String = "sqlite-database",
    createStatement: String,
    insertStatement: String
  ): VirtualFile = runWriteAction {
    createEmptyTempSqliteDatabase(dbName).also { file ->
      // Note: We need to close the connection so the database file handle is released by the Sqlite
      // engine.
      openSqliteDatabase(file).use { connection ->
        fillAdHocDatabase(connection, createStatement, insertStatement)
      }

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  fun createTestSqliteDatabaseWithConfigurableTypes(
    dbName: String = "sqlite-database",
    tableName: String = "tab",
    types: List<String> = emptyList()
  ): VirtualFile = runWriteAction {
    createEmptyTempSqliteDatabase(dbName).also { file ->
      // Note: We need to close the connection so the database file handle is released by the Sqlite
      // engine.
      openSqliteDatabase(file).use { connection ->
        createTestDBWithConfigurableTypes(connection, tableName, types)
      }

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  private fun createEmptyTempSqliteDatabase(name: String): VirtualFile = runReadAction {
    tempDirTestFixture.createFile(name).also { file ->

      // Note: We need to close the connection so the database file handle is released by the Sqlite
      // engine.
      openSqliteDatabase(file).use { connection ->
        // Create then drop a test table so this file is not empty on disk.
        connection.createStatement().use { stmt ->
          val sql = "CREATE TABLE test (\n" + " id integer PRIMARY KEY\n" + ");"
          stmt.executeUpdate(sql)

          stmt.executeUpdate("DROP TABLE test")
        }
      }

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  fun createTempBinaryFile(size: Int): VirtualFile = runWriteAction {
    tempDirTestFixture.createFile("sqlite-database").also { file ->
      file.getOutputStream(this).use { stream ->
        for (i in 0 until size) {
          stream.write(i % 255)
        }
      }
      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  private fun fillConfigurableTestDB(
    connection: Connection,
    tableName: String,
    columns: List<String>,
    primaryKeys: List<String>,
    withoutRowId: Boolean = false
  ) {
    var columnsString =
      primaryKeys.joinToString(
        separator = ", ",
        postfix = if (primaryKeys.isNotEmpty() && columns.isNotEmpty()) ", " else " "
      ) {
        "${AndroidSqlLexer.getValidName(it)} INTEGER NOT NULL"
      }
    columnsString += columns.joinToString(separator = ", ") { AndroidSqlLexer.getValidName(it) }

    val primaryKeysNames =
      primaryKeys.joinToString(separator = ",") { AndroidSqlLexer.getValidName(it) }

    val createTableStatement =
      "CREATE TABLE ${AndroidSqlLexer.getValidName(tableName)} ( $columnsString " +
        (if (primaryKeys.isNotEmpty()) ", PRIMARY KEY ( $primaryKeysNames ) " else "") +
        " ) " +
        if (withoutRowId) " WITHOUT rowid" else ""

    connection.createStatement().use { stmt -> stmt.executeUpdate(createTableStatement) }

    var colsNames =
      primaryKeys.joinToString(
        separator = ", ",
        postfix = if (primaryKeys.isNotEmpty() && columns.isNotEmpty()) ", " else ""
      ) {
        AndroidSqlLexer.getValidName(it)
      }
    colsNames += columns.joinToString(separator = ", ") { AndroidSqlLexer.getValidName(it) }

    var colsValues =
      primaryKeys.joinToString(
        separator = ", ",
        postfix = if (primaryKeys.isNotEmpty() && columns.isNotEmpty()) ", " else ""
      ) {
        "?"
      }
    colsValues += columns.joinToString(separator = ", ") { "?" }

    val insertStatement =
      "INSERT INTO ${AndroidSqlLexer.getValidName(tableName)} ( $colsNames ) VALUES ( $colsValues )"

    var index = 1
    connection.prepareStatement(insertStatement).use { preparedStatement ->
      repeat(primaryKeys.size) {
        preparedStatement.setInt(index, index)
        index += 1
      }
      repeat(columns.size) {
        preparedStatement.setString(index, "val $index")
        index += 1
      }
      preparedStatement.execute()
    }
  }

  private fun fillAdHocDatabase(
    connection: Connection,
    createStatement: String,
    insertStatement: String
  ) {
    connection.createStatement().use { statement -> statement.executeUpdate(createStatement) }

    connection.createStatement().use { statement -> statement.execute(insertStatement) }
  }

  private fun createTestDBWithConfigurableTypes(
    connection: Connection,
    tableName: String,
    types: List<String>
  ) {
    connection.createStatement().use { stmt ->
      val columns = types.mapIndexed { index, type -> "column$index $type" }.joinToString(",")

      val sql = "CREATE TABLE $tableName ( $columns );"
      stmt.executeUpdate(sql)
    }
  }

  private fun fillTestDatabase(connection: Connection) = runWriteAction {
    connection.createStatement().use { stmt ->
      val sql =
        "CREATE TABLE Author (\n" +
          " author_id integer PRIMARY KEY,\n" +
          " first_name text NOT NULL,\n" +
          " last_name text NOT NULL\n" +
          ");"
      stmt.executeUpdate(sql)
    }

    connection.createStatement().use { stmt ->
      val sql =
        "CREATE TABLE Book (\n" +
          " book_id integer PRIMARY KEY,\n" +
          " title text NOT NULL,\n" +
          " isbn text NOT NULL,\n" +
          " author_id integer NOT NULL,\n" +
          " FOREIGN KEY (author_id) REFERENCES Author (author_id) \n" +
          ");"
      stmt.executeUpdate(sql)
    }

    // Batch insert a bunch of rows
    connection.autoCommit = false
    val authorSql = "INSERT INTO author(first_name, last_name) VALUES(?, ?)"
    connection.prepareStatement(authorSql).use { pstmt ->
      addAuthor(pstmt, "Joe1", "LastName1")
      addAuthor(pstmt, "Joe2", "LastName2")
      addAuthor(pstmt, "Joe3", "LastName3")
      addAuthor(pstmt, "Joe4", "LastName4")
      addAuthor(pstmt, "Joe5", "LastName5")
      pstmt.executeBatch()
    }
    val sql = "INSERT INTO Book(book_id, title, isbn, author_id) VALUES(?, ?, ?, ?)"
    connection.prepareStatement(sql).use { pstmt ->
      addBook(pstmt, 1, "MyTitle1", "12345-1", 1)
      addBook(pstmt, 2, "MyTitle2", "12345-2", 2)
      addBook(pstmt, 3, "MyTitle3", "12345-3", 2)
      addBook(pstmt, 4, "MyTitle4", "12345-4", 3)
      pstmt.executeBatch()
    }
    connection.commit()
    connection.autoCommit = true
  }

  private fun addBook(
    stmt: PreparedStatement,
    id: Int,
    title: String,
    isbn: String,
    authorId: Int
  ) {
    stmt.setInt(1, id)
    stmt.setString(2, title)
    stmt.setString(3, isbn)
    stmt.setInt(4, authorId)
    stmt.addBatch()
  }

  private fun addAuthor(stmt: PreparedStatement, firstName: String, lastName: String) {
    stmt.setString(1, firstName)
    stmt.setString(2, lastName)
    stmt.addBatch()
  }

  private fun openSqliteDatabase(file: VirtualFile): Connection {
    // db parameters
    val url = "jdbc:sqlite:" + file.path

    // create a connection to the database
    return DriverManager.getConnection(url)
  }
}
