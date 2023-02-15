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
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.io.delete
import java.lang.System.lineSeparator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor

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

    val sqliteCliSrcPath = SqliteCliProviderImpl(project).getSqliteCli()!!
    val sqliteCliDstPath =
      copySqliteCliToTmpDir("new sqlite3 location with spaces", sqliteCliSrcPath)
    client = SqliteCliClientImpl(sqliteCliDstPath, taskExecutor.asCoroutineDispatcher())
  }

  /**
   * Copies `sqliteCli3` tool with its parent folder to a temporary location. This allows for e.g.
   * inserting a spaces in the new location path to ensure the code works under these circumstances.
   */
  private fun copySqliteCliToTmpDir(
    @Suppress("SameParameterValue") dirName: String,
    sqliteCliSrcPath: Path
  ): Path {
    // Copy directory with content
    val tempDirPath = Paths.get(tempDirTestFixture.tempDirPath)
    val toolsDstPath = Files.createTempDirectory(tempDirPath, dirName)
    val toolsSrcPath = sqliteCliSrcPath.parent
    val copyingSuccess = toolsSrcPath.toFile().copyRecursively(toolsDstPath.toFile())
    assertWithMessage(
        "Verifying if sqlite3 folder was successfully copied. Expecting success = true."
      )
      .that(copyingSuccess)
      .isTrue()

    // Copy sqlite3 executable again, with COPY_ATTRIBUTES enabled to ensure the file is executable.
    // There is no other clean solution for this and the file is small, so no harm to repeat this
    // work.
    val sqliteCliDstPath = toolsDstPath.resolve(sqliteCliSrcPath.fileName)
    Files.copy(sqliteCliSrcPath, sqliteCliDstPath, COPY_ATTRIBUTES, REPLACE_EXISTING)

    return sqliteCliDstPath
  }

  override fun tearDown() {
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  /**
   * Tests a basic end-to-end scenario of creating a database and a table. Uses:
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
      assertThat(response.stdOutput)
        .ignoringCase()
        .isEqualTo(
          "PRAGMA foreign_keys=OFF;" +
            lineSeparator() +
            "BEGIN TRANSACTION;" +
            lineSeparator() +
            "CREATE TABLE IF NOT EXISTS '$table1' ('$column1' int, '$column2' int);" +
            lineSeparator() +
            "COMMIT;"
        )
    }
  }

  /**
   * Tests a large output printed to std-out which if not handled property would hang on Windows.
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
      SqliteCliArgs.builder()
        .database(databaseFile)
        .raw("create table '$table1' ('$column1' int);")
        .build()
    )

    // populate the table with values
    client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .apply { values.forEach { raw("insert into '$table1' values ($it);") } }
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // query the database and verify output
    run {
      val response =
        client.runSqliteCliCommand(
          SqliteCliArgs.builder().database(databaseFile).queryTableContents(table1).build()
        )
      assertThat(response.exitCode).isEqualTo(0)
      assertThat(response.errOutput).isEmpty()
      assertThat(response.stdOutput).isEqualTo(expectedOutput)
    }
  }

  /**
   * Tests CSV export. Uses:
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
        SqliteCliArgs.builder()
          .database(databaseFile)
          .raw("create table '$table1' ('$column1' int, '$column2' text, '$column3' int);")
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // populate table
    client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .raw("insert into '$table1' values (1,2,3);")
          .raw("insert into '$table1' values (4,5,6);")
          .raw("insert into '$table1' values (7,8,9);")
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // export to csv file - no headers, separator=|
    val outputFile1 = tempDirTestFixture.createFile(outputFile1).toNioPath().also { it.delete() }
    client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .modeCsv()
          .output(outputFile1)
          .separator('|')
          .queryTableContents(table1)
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // export to csv file - with headers, separator=;
    val outputFile2 = tempDirTestFixture.createFile(outputFile2).toNioPath().also { it.delete() }
    client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .modeCsv()
          .headersOn()
          .output(outputFile2)
          .separator(';')
          .queryTableContents(table1)
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // verify content no headers, separator=|
    assertThat(outputFile1.toLines().toList()).isEqualTo(listOf("1|2|3", "4|5|6", "7|8|9"))

    // verify content with headers, separator=;
    assertThat(outputFile2.toLines())
      .isEqualTo(
        listOf(
          "\"$column1\";\"$column2\";\"$column3\"",
          "1;2;3",
          "4;5;6",
          "7;8;9",
        )
      )
  }

  /**
   * Tests cloning a database and exporting a database as a SQL script Uses:
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
        SqliteCliArgs.builder()
          .database(databaseFile)
          .raw("create table '$table1' ('$column1' int, '$column2' text, '$column3' float);")
          .raw("create table '$table2' ('$column11' int, '$column22' text, '$column33' blob);")
          .raw("create view '$view1' as select * from '$table1';")
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // populate tables
    client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .raw("insert into '$table1' values (1,2,3);")
          .raw("insert into '$table1' values (4,5,6);")
          .raw("insert into '$table2' values (11,22,33);")
          .raw("insert into '$table2' values (44,55,66);")
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // query table list
    client.runSqliteCliCommand(
        SqliteCliArgs.builder().database(databaseFile).queryTableList().build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput).isEqualTo(table1 + lineSeparator() + table2)
      }

    // query view list
    client.runSqliteCliCommand(
        SqliteCliArgs.builder().database(databaseFile).queryViewList().build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput).isEqualTo(view1)
      }

    // dump table
    client.runSqliteCliCommand(
        SqliteCliArgs.builder().database(databaseFile).dumpTable(table1).build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
        assertThat(it.stdOutput)
          .ignoringCase()
          .isEqualTo(
            "PRAGMA foreign_keys=OFF;" +
              lineSeparator() +
              "BEGIN TRANSACTION;" +
              lineSeparator() +
              "CREATE TABLE IF NOT EXISTS '$table1' ('$column1' int, '$column2' text, '$column3' float);" +
              lineSeparator() +
              "INSERT INTO \"$table1\" VALUES(1,'2',3.0);" +
              lineSeparator() +
              "INSERT INTO \"$table1\" VALUES(4,'5',6.0);" +
              lineSeparator() +
              "COMMIT;"
          )
      }

    // dump everything
    client.runSqliteCliCommand(SqliteCliArgs.builder().database(databaseFile).dump().build()).also {
      assertThat(it.exitCode).isEqualTo(0)
      assertThat(it.errOutput).isEmpty()
      assertThat(it.stdOutput)
        .ignoringCase()
        .isEqualTo(
          "PRAGMA foreign_keys=OFF;" +
            lineSeparator() +
            "BEGIN TRANSACTION;" +
            lineSeparator() +
            "CREATE TABLE IF NOT EXISTS '$table1' ('$column1' int, '$column2' text, '$column3' float);" +
            lineSeparator() +
            "INSERT INTO \"$table1\" VALUES(1,'2',3.0);" +
            lineSeparator() +
            "INSERT INTO \"$table1\" VALUES(4,'5',6.0);" +
            lineSeparator() +
            "CREATE TABLE IF NOT EXISTS '$table2' ('$column11' int, '$column22' text, '$column33' blob);" +
            lineSeparator() +
            "INSERT INTO \"$table2\" VALUES(11,'22',33);" +
            lineSeparator() +
            "INSERT INTO \"$table2\" VALUES(44,'55',66);" +
            lineSeparator() +
            "CREATE VIEW '$view1' as select * from '$table1';" +
            lineSeparator() +
            "COMMIT;"
        )
    }

    // clone the database
    val databaseClone = tempDirTestFixture.createFile(dbClonePath).toNioPath().also { it.delete() }

    client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .raw(".clone '${databaseClone.toAbsolutePath()}'")
          .build()
      )
      .also {
        assertThat(it.exitCode).isEqualTo(0)
        assertThat(it.errOutput).isEmpty()
      }

    // check if clone the same as original
    val (dump1, dump2) =
      listOf(databaseFile, databaseClone).map { path ->
        client.runSqliteCliCommand(SqliteCliArgs.builder().database(path).dump().build()).let {
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
    // Windows won't produce an error message in this scenario if there are non-ASCII characters in
    // the file name.
    // Low impact, so leaving it as is for now and just keeping Windows file name simpler. TODO:
    // maybe revisit sometime
    val dstFileName = if (!SystemInfo.isWindows) outputFile1 else "simple-file-name"
    val dstPath = tempDirTestFixture.createFile(dstFileName).toNioPath()

    val response =
      client.runSqliteCliCommand(
        SqliteCliArgs.builder()
          .database(databaseFile)
          .raw(".clone '${dstPath.toAbsolutePath()}'")
          .build()
      )

    assertWithMessage(
        "Inspecting exit code from the command. Expecting error code 0 (counter intuitively)."
      )
      .that(response.exitCode)
      .isEqualTo(0)

    assertWithMessage(
        "Inspecting response from the command. Expecting an error indication. Actual: $response."
      )
      .that(response.errOutput)
      .contains(dstPath.toString())
  }
}
