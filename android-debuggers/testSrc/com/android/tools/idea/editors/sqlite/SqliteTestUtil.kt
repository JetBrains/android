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
package com.android.tools.idea.editors.sqlite

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.TempDirTestFixture
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

class SqliteTestUtil (private val tempDirTestFixture: TempDirTestFixture) {

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

  fun createTempSqliteDatabase(name: String = "sqlite-database"): VirtualFile = runWriteAction {
    createEmptyTempSqliteDatabase(name).also { file ->
      // Note: We need to close the connection so the database file handle is released by the Sqlite engine.
      openSqliteDatabase(file).use(::fillTestDatabase)

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  fun createTestSqliteDatabase(name: String = "sqlite-database"): VirtualFile = runWriteAction {
    createEmptyTempSqliteDatabase(name).also { file ->
      // Note: We need to close the connection so the database file handle is released by the Sqlite engine.
      openSqliteDatabase(file).use(::fillTestDatabase)

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
    }
  }

  fun createEmptyTempSqliteDatabase(name: String): VirtualFile = runReadAction {
    tempDirTestFixture.createFile(name).also { file ->

      // Note: We need to close the connection so the database file handle is released by the Sqlite engine.
      openSqliteDatabase(file).use { connection ->
        // Create then drop a test table so this file is not empty on disk.
        connection.createStatement().use { stmt ->
          val sql = "CREATE TABLE test (\n" +
                    " id integer PRIMARY KEY\n" +
                    ");"
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

  private fun fillDatabase(connection: Connection) = runWriteAction {
    connection.createStatement().use { stmt ->
      val sql = "CREATE TABLE contacts (\n" +
          " contact_id integer PRIMARY KEY,\n" +
          " first_name text NOT NULL,\n" +
          " last_name text NOT NULL,\n" +
          " email text NOT NULL UNIQUE,\n" +
          " phone text NOT NULL UNIQUE\n" +
          ");"
      stmt.executeUpdate(sql)
    }

    // Batch insert a bunch of rows
    connection.autoCommit = false
    val sql = "INSERT INTO contacts(contact_id, first_name, last_name, email, phone) VALUES(?, ?, ?, ?, ?)"
    connection.prepareStatement(sql).use { pstmt ->
      // 300 rows so the file size is non trivial (currently about 40KB)
      for (i in 0..299) {
        pstmt.setInt(1, i * 10)
        pstmt.setString(2, "MyName $i")
        pstmt.setString(3, "MyLastName $i")
        pstmt.setString(4, "MyEmail@$i")
        pstmt.setString(5, "MyPhone: 555-$i")
        pstmt.addBatch()
      }
      pstmt.executeBatch()
    }
    connection.commit()
    connection.autoCommit = true
  }

  private fun fillTestDatabase(connection: Connection) = runWriteAction {
    connection.createStatement().use { stmt ->
      val sql = "CREATE TABLE Author (\n" +
          " author_id integer PRIMARY KEY,\n" +
          " first_name text NOT NULL,\n" +
          " last_name text NOT NULL\n" +
          ");"
      stmt.executeUpdate(sql)
    }

    connection.createStatement().use { stmt ->
      val sql = "CREATE TABLE Book (\n" +
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

  private fun addBook(stmt: PreparedStatement, id: Int, title: String, isbn: String, authorId: Int) {
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