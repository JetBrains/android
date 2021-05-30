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
package com.android.tools.idea.sqlite.cli

import com.android.tools.idea.sqlite.utils.initAdbFileProvider
import com.android.tools.idea.sqlite.utils.toLines
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.io.delete
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor
import java.lang.System.lineSeparator
import java.nio.file.Path

class SqliteCliClientTest : LightPlatformTestCase() {
  private lateinit var client: SqliteCliClient
  private lateinit var databaseFile: Path
  private lateinit var tempDirTestFixture: TempDirTestFixture

  private val taskExecutor = PooledThreadExecutor.INSTANCE

  private val trickyChars = " ąę  ść źż"
  private val table1 = "t1$trickyChars"
  private val table2 = "t2$trickyChars"
  private val view1 = "v1$trickyChars"
  private val column1 = "c1$trickyChars"
  private val column2 = "c2$trickyChars"
  private val column3 = "c3$trickyChars"
  private val column11 = "c11$trickyChars"
  private val column22 = "c22$trickyChars"
  private val column33 = "c33$trickyChars"
  private val dbPath = "db $trickyChars"
  private val dbClonePath = "$dbPath clone $trickyChars"
  private val outputFile1 = "of1$trickyChars"
  private val outputFile2 = "of2$trickyChars"

  override fun setUp() {
    super.setUp()
    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()
    databaseFile = tempDirTestFixture.createFile(dbPath).toNioPath()
    initAdbFileProvider(project)
    client = SqliteCliClientImpl(SqliteCliProviderImpl(project).getSqliteCli()!!, taskExecutor.asCoroutineDispatcher())
  }

  override fun tearDown() {
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  /**
   * Tests a basic end-to-end scenario of creating a database and a table.
   * Uses:
   * - [SqliteCliArgs.Builder.database]
   * - [SqliteCliArgs.Builder.dump]
   * - [SqliteCliArgs.Builder.raw]
   */
  fun testCreateDatabase() = runBlocking {
    val createStatement = "create table '$table1' ('$column1' int, '$column2' int);"

    // create a table
    run {
      val args = SqliteCliArgs.builder().database(databaseFile).raw(createStatement).build()
      val response = client.runSqliteCliCommand(args)
      assertThat(response.exitCode).isEqualTo(0)
      assertThat(response.errOutput).isEmpty()
      assertThat(response.stdOutput).isEmpty()
    }

    // verify the table was created through a dump command
    run {
      val args = SqliteCliArgs.builder().database(databaseFile).dump().build()
      val response = client.runSqliteCliCommand(args)
      assertThat(response.exitCode).isEqualTo(0)
      assertThat(response.errOutput).isEmpty()
      assertThat(response.stdOutput).ignoringCase().isEqualTo(
        "PRAGMA foreign_keys=OFF;" + lineSeparator() +
        "BEGIN TRANSACTION;" + lineSeparator() +
        "CREATE TABLE IF NOT EXISTS '$table1' ('$column1' int, '$column2' int);" + lineSeparator() +
        "COMMIT;"
      )
    }
  }

  /** Tests a large output printed to std-out which if not handled property would hang on Windows.
   * Uses:
   * - [SqliteCliArgs.Builder.database]
   * - [SqliteCliArgs.Builder.queryTableContents]
   * - [SqliteCliArgs.Builder.raw]
   */
  fun testQueryLargeOutput() = runBlocking {
    // generate a "large" output
    val values = (1..2000).toList()
    val expectedOutput = values.joinToString(separator = lineSeparator())

    // create table
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .raw("create table '$table1' ('$column1' int);")
        .build()
    )

    // populate the table with values
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .apply { values.forEach { raw("insert into '$table1' values ($it);") } }
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // query the database and verify output
    run {
      val response = client.runSqliteCliCommand(
        SqliteCliArgs
          .builder()
          .database(databaseFile)
          .queryTableContents(table1)
          .build())
      assertThat(response.exitCode).isEqualTo(0)
      assertThat(response.errOutput).isEmpty()
      assertThat(response.stdOutput).isEqualTo(expectedOutput)
    }
  }

  /**
   * Tests CSV export.
   * Uses:
   * - [SqliteCliArgs.Builder.database]
   * - [SqliteCliArgs.Builder.headersOn]
   * - [SqliteCliArgs.Builder.modeCsv]
   * - [SqliteCliArgs.Builder.output]
   * - [SqliteCliArgs.Builder.queryTableContents]
   * - [SqliteCliArgs.Builder.raw]
   * - [SqliteCliArgs.Builder.separator]
   */
  fun testExportToCsv() = runBlocking {
    // create table
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .raw("create table '$table1' ('$column1' int, '$column2' text, '$column3' int);")
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // populate table
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .raw("insert into '$table1' values (1,2,3);")
        .raw("insert into '$table1' values (4,5,6);")
        .raw("insert into '$table1' values (7,8,9);")
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // export to csv file - no headers, separator=|
    val outputFile1 = tempDirTestFixture.createFile(outputFile1).toNioPath().also { it.delete() }
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .modeCsv()
        .output(outputFile1)
        .separator('|')
        .queryTableContents(table1)
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // export to csv file - with headers, separator=;
    val outputFile2 = tempDirTestFixture.createFile(outputFile2).toNioPath().also { it.delete() }
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .modeCsv()
        .headersOn()
        .output(outputFile2)
        .separator(';')
        .queryTableContents(table1)
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // verify content no headers, separator=|
    assertThat(outputFile1.toLines().toList()).isEqualTo(listOf(
      "1|2|3",
      "4|5|6",
      "7|8|9"))

    // verify content with headers, separator=;
    assertThat(outputFile2.toLines()).isEqualTo(listOf(
      "\"$column1\";\"$column2\";\"$column3\"",
      "1;2;3",
      "4;5;6",
      "7;8;9",
    ))
  }

  /**
   * Tests cloning a database and exporting a database as a SQL script
   * Uses:
   * - [SqliteCliArgs.Builder.clone]
   * - [SqliteCliArgs.Builder.database]
   * - [SqliteCliArgs.Builder.dumpTable]
   * - [SqliteCliArgs.Builder.dump]
   * - [SqliteCliArgs.Builder.queryTableList]
   * - [SqliteCliArgs.Builder.queryViewList]
   * - [SqliteCliArgs.Builder.raw]
   */
  fun testCloneDatabaseExportToSql() = runBlocking {
    // create tables
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .raw("create table '$table1' ('$column1' int, '$column2' text, '$column3' float);")
        .raw("create table '$table2' ('$column11' int, '$column22' text, '$column33' blob);")
        .raw("create view '$view1' as select * from '$table1';")
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // populate tables
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .raw("insert into '$table1' values (1,2,3);")
        .raw("insert into '$table1' values (4,5,6);")
        .raw("insert into '$table2' values (11,22,33);")
        .raw("insert into '$table2' values (44,55,66);")
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // query table list
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .queryTableList()
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput).isEqualTo(table1 + lineSeparator() + table2)
      }

    // query view list
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .queryViewList()
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput).isEqualTo(view1)
      }

    // dump table
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .dumpTable(table1)
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput).ignoringCase().isEqualTo(
          "PRAGMA foreign_keys=OFF;" + lineSeparator() +
          "BEGIN TRANSACTION;" + lineSeparator() +
          "CREATE TABLE IF NOT EXISTS '$table1' ('$column1' int, '$column2' text, '$column3' float);" + lineSeparator() +
          "INSERT INTO \"$table1\" VALUES(1,'2',3.0);" + lineSeparator() +
          "INSERT INTO \"$table1\" VALUES(4,'5',6.0);" + lineSeparator() +
          "COMMIT;"
        )
      }

    // dump everything
    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .dump()
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput).ignoringCase().isEqualTo(
          "PRAGMA foreign_keys=OFF;" + lineSeparator() +
          "BEGIN TRANSACTION;" + lineSeparator() +
          "CREATE TABLE IF NOT EXISTS '$table1' ('$column1' int, '$column2' text, '$column3' float);" + lineSeparator() +
          "INSERT INTO \"$table1\" VALUES(1,'2',3.0);" + lineSeparator() +
          "INSERT INTO \"$table1\" VALUES(4,'5',6.0);" + lineSeparator() +
          "CREATE TABLE IF NOT EXISTS '$table2' ('$column11' int, '$column22' text, '$column33' blob);" + lineSeparator() +
          "INSERT INTO \"$table2\" VALUES(11,'22',33);" + lineSeparator() +
          "INSERT INTO \"$table2\" VALUES(44,'55',66);" + lineSeparator() +
          "CREATE VIEW '$view1' as select * from '$table1';" + lineSeparator() +
          "COMMIT;"
        )
      }

    // clone the database
    val databaseClone = tempDirTestFixture.createFile(dbClonePath).toNioPath().also { it.delete() }

    client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .clone(databaseClone)
        .build())
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // check if clone the same as original
    val (dump1, dump2) = listOf(databaseFile, databaseClone).map { path ->
      client.runSqliteCliCommand(
        SqliteCliArgs
          .builder()
          .database(path)
          .dump()
          .build())
        .let {
          assertThat(it.exitCode).isEqualTo(0)
          assertThat(it.errOutput).isEmpty()
          it.stdOutput
        }
    }
    assertThat(dump1).isEqualTo(dump2)
  }

  /**
   * Captures a scenario in which [SqliteCliResponse.exitCode] is `0` despite the command failing.
   * The error can still be detected by inspecting [SqliteCliResponse.errOutput].
   */
  fun testErrorMessageAndExitCodeSuccess() = runBlocking {
    val dstPath = tempDirTestFixture.createFile(outputFile1).toNioPath()

    val response = client.runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databaseFile)
        .clone(dstPath)
        .build())

    assertThat(response.exitCode).isEqualTo(0)
    assertThat(response.errOutput).contains(dstPath.toString())
  }
}