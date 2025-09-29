/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers

import com.android.tools.datastore.database.UnifiedEventsTable
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.fail

class ImportedSessionUtilsTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `getDbMetadata reads metadata correctly`() {
    // Arrange
    val dbFile = temporaryFolder.newFile("metadata.db")
    val expectedMetadata = mapOf(
      "task_type" to "JAVA_KOTLIN_ALLOCATIONS",
      "start_time" to "123456789"
    )

    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
      connection.createStatement().use { stmt ->
        stmt.execute("CREATE TABLE _metadata (key TEXT, value TEXT)")
        expectedMetadata.forEach { (key, value) ->
          connection.prepareStatement("INSERT INTO _metadata (key, value) VALUES (?, ?)").use { insertStmt ->
            insertStmt.setString(1, key)
            insertStmt.setString(2, value)
            insertStmt.executeUpdate()
          }
        }
      }
    }

    // Act
    val actualMetadata = ImportedSessionUtils.getDbMetadata(dbFile)

    // Assert
    assertThat(actualMetadata).isEqualTo(expectedMetadata)
  }

  @Test
  fun `getDbMetadata handles missing table or invalid file gracefully`() {
    // Arrange: Create a file that is not a valid SQLite DB with the required table.
    val invalidFile = temporaryFolder.newFile("not_a_db.txt").apply { writeText("Hello, World!") }

    // Act & Assert: The function should return an empty map without throwing an exception.
    assertThat(ImportedSessionUtils.getDbMetadata(invalidFile)).isEmpty()
  }

  @Test
  fun `getSessionInfoFromDb reads info correctly`() {
    // Arrange
    val dbFile = temporaryFolder.newFile("session_info.db")
    val expectedExposureLevel = Common.Process.ExposureLevel.PROFILEABLE
    val expectedJvmtiEnabled = true
    val sessionStartEvent = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.SESSION
      session = Common.SessionData.newBuilder().apply {
        sessionStarted = Common.SessionData.SessionStarted.newBuilder().apply {
          exposureLevel = expectedExposureLevel
          jvmtiEnabled = expectedJvmtiEnabled
        }.build()
      }.build()
    }.build()

    createDbWithEvents(dbFile, sessionStartEvent)

    // Act
    val sessionData = ImportedSessionUtils.getDbConnectionFromFile(dbFile).use {
      ImportedSessionUtils.getSessionInfoFromDb(it)
    }

    // Assert
    assertThat(sessionData).isEqualTo(sessionStartEvent.session)
  }

  @Test
  fun `getSessionInfoFromDb returns null for missing session event`() {
    // Arrange
    val dbFile = temporaryFolder.newFile("no_session_info.db")
    // Create a DB with a non-session event
    createDbWithEvents(dbFile, Common.Event.newBuilder().setKind(Common.Event.Kind.CPU_TRACE).build())

    // Act
    val sessionData = ImportedSessionUtils.getDbConnectionFromFile(dbFile).use {
      ImportedSessionUtils.getSessionInfoFromDb(it)
    }

    // Assert
    assertThat(sessionData).isNull()
  }

  @Test
  fun `getTimestampRangeFromDbFile reads range correctly`() {
    // Arrange
    val dbFile = temporaryFolder.newFile("timestamps.db")
    val minTimestamp = 100L
    val maxTimestamp = 500L
    createDbWithEvents(
      dbFile,
      Common.Event.newBuilder().setTimestamp(minTimestamp).build(),
      Common.Event.newBuilder().setTimestamp(300L).build(),
      Common.Event.newBuilder().setTimestamp(maxTimestamp).build()
    )

    // Act
    val (actualMin, actualMax) = ImportedSessionUtils.getTimestampRangeFromDbFile(dbFile) ?: fail("Timestamp range should not be null")

    // Assert
    assertThat(actualMin).isEqualTo(minTimestamp)
    assertThat(actualMax).isEqualTo(maxTimestamp)
  }

  @Test
  fun `getTimestampRangeFromDbFile returns null for empty db`() {
    // Arrange
    val dbFile = temporaryFolder.newFile("empty.db")
    createDbWithEvents(dbFile) // Create table but no events

    // Act & Assert
    assertThat(ImportedSessionUtils.getTimestampRangeFromDbFile(dbFile)).isNull()
  }

  @Test
  fun `getTimestampRangeFromDbFile returns null for invalid file`() {
    // Arrange
    val invalidFile = temporaryFolder.newFile("not_a_db.txt").apply { writeText("Hello, World!") }

    // Act & Assert
    assertThat(ImportedSessionUtils.getTimestampRangeFromDbFile(invalidFile)).isNull()
  }

  private fun createDbWithEvents(dbFile: File, vararg events: Common.Event) {
    try {
      DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
        val table = UnifiedEventsTable().apply { initialize(connection) }
        events.forEach { table.insertUnifiedEvent(1L, it) }
      }
    }
    catch (e: SQLException) {
      fail("Test setup failed to create database: ${e.message}")
    }
  }
}