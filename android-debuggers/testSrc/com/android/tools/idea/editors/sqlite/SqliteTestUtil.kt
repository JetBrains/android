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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.TempDirTestFixture
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class SqliteTestUtil(private val tempDirTestFixture: TempDirTestFixture) {

  @Throws(IOException::class)
  fun createByteSequence(file: VirtualFile, size: Int): ByteSequence {
    return ApplicationManager.getApplication().runReadAction(ThrowableComputable<ByteSequence, IOException> {
      val bytes = ByteArray(size)
      file.inputStream.use { stream ->
        val length = stream.read(bytes)
        ByteArraySequence(bytes, 0, length)
      }
    })
  }

  @Throws(SQLException::class)
  fun createTempSqliteDatabase(): VirtualFile {
    return ApplicationManager.getApplication().runWriteAction(ThrowableComputable<VirtualFile, SQLException> {
      val file = createEmptyTempSqliteDatabase()

      // Note: We need to close the connection so the database file handle is released by the
      // Sqlite engine.
      openSqliteDatabase(file).use { connection -> fillDatabase(connection) }

      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
      file
    })
  }

  @Throws(SQLException::class)
  fun createEmptyTempSqliteDatabase(): VirtualFile {
    return ApplicationManager.getApplication().runWriteAction(ThrowableComputable<VirtualFile, SQLException> {
      val file = tempDirTestFixture.createFile("sqlite-database")

      // Note: We need to close the connection so the database file handle is released by the
      // Sqlite engine.
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
      file
    })
  }

  @Throws(IOException::class)
  fun createTempBinaryFile(size: Int): VirtualFile {
    return ApplicationManager.getApplication().runWriteAction(ThrowableComputable<VirtualFile, IOException> {
      val file = tempDirTestFixture.createFile("sqlite-database")
      file.getOutputStream(this).use { stream ->
        for (i in 0 until size) {
          stream.write(i % 255)
        }
      }
      // File as changed on disk, refresh virtual file cached data
      file.refresh(false, false)
      file
    })
  }

  @Throws(SQLException::class)
  private fun fillDatabase(connection: Connection) {
    ApplicationManager.getApplication().runWriteAction {
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
          pstmt.setString(2, "MyName " + i)
          pstmt.setString(3, "MyLastName " + i)
          pstmt.setString(4, "MyEmail@" + i)
          pstmt.setString(5, "MyPhone: 555-" + i)
          pstmt.addBatch()
        }
        pstmt.executeBatch()
      }
      connection.commit()
      connection.autoCommit = true
    }
  }

  @Throws(SQLException::class)
  private fun openSqliteDatabase(file: VirtualFile): Connection {
    // db parameters
    val url = "jdbc:sqlite:" + file.path

    // create a connection to the database
    return DriverManager.getConnection(url)
  }
}