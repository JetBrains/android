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
package com.android.tools.idea.sqlite.controllers

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.cli.SqliteCliClient
import com.android.tools.idea.sqlite.cli.SqliteCliClientImpl
import com.android.tools.idea.sqlite.cli.SqliteCliProviderImpl
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.mocks.CliDatabaseConnection
import com.android.tools.idea.sqlite.mocks.FakeExportToFileDialogView
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.Delimiter.COMMA
import com.android.tools.idea.sqlite.model.Delimiter.TAB
import com.android.tools.idea.sqlite.model.Delimiter.VERTICAL_BAR
import com.android.tools.idea.sqlite.model.ExportFormat.CSV
import com.android.tools.idea.sqlite.model.ExportRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportDatabaseRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportQueryResultsRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportTableRequest
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.initAdbFileProvider
import com.android.tools.idea.sqlite.utils.toLines
import com.android.tools.idea.sqlite.utils.unzipTo
import com.android.tools.idea.testing.runDispatching
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.jetbrains.ide.PooledThreadExecutor
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executor

private const val nonAsciiSuffix = " ąę"
private const val table1 = "t1$nonAsciiSuffix"
private const val table2 = "t2$nonAsciiSuffix"
private const val table3 = "t3$nonAsciiSuffix"
private const val column1 = "c1$nonAsciiSuffix"
private const val column2 = "c2$nonAsciiSuffix"
private const val databaseFileName = "db$nonAsciiSuffix.db"
private const val outputFileName = "output$nonAsciiSuffix.out"

/** Keeps connection ids unique */
private val nextConnectionId: () -> Int = run { var next = 1; { next++ } }

class ExportToFileControllerTest : LightPlatformTestCase() {
  private lateinit var notifyExportComplete: (ExportRequest) -> Unit
  private lateinit var notifyExportError: (ExportRequest, Throwable?) -> Unit

  private lateinit var tempDirTestFixture: TempDirTestFixture

  private lateinit var edtExecutor: Executor
  private lateinit var taskExecutor: Executor

  private lateinit var databaseRepository: DatabaseRepository
  private lateinit var sqliteCliClient: SqliteCliClient

  private lateinit var view: FakeExportToFileDialogView
  private lateinit var controller: ExportToFileController

  override fun setUp() {
    super.setUp()

    notifyExportComplete = mock()
    notifyExportError = { request, throwable ->
      throw IllegalStateException("Error while processing a request ($request).", throwable)
    }

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()

    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = PooledThreadExecutor.INSTANCE

    initAdbFileProvider(project)
    sqliteCliClient = SqliteCliClientImpl(SqliteCliProviderImpl(project).getSqliteCli()!!, taskExecutor.asCoroutineDispatcher())
    databaseRepository = OpenDatabaseRepository(project, taskExecutor)

    view = FakeExportToFileDialogView()
    controller = ExportToFileController(
      project,
      view,
      databaseRepository,
      taskExecutor,
      edtExecutor,
      notifyExportComplete,
      notifyExportError
    )
    controller.setUp()
    Disposer.register(testRootDisposable, controller)
  }

  override fun tearDown() {
    runDispatching { databaseRepository.clear() }
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  fun testExportQueryToCsvFileDb() = testExportQueryToCsv(createEmptyDatabase(DatabaseType.File))

  fun testExportQueryToCsvLiveDb() = testExportQueryToCsv(createEmptyDatabase(DatabaseType.Live))

  private fun testExportQueryToCsv(database: SqliteDatabaseId) {
    val values = (1..10).toTwoColumnTable()
    fillDatabase(database, table1, values)

    val statement = createSqliteStatement("select * from '$table1' where cast(\"$column1\" as text) > cast(5 as text)")
    val dstPath = tempDirTestFixture.toNioPath().resolve(outputFileName)
    val exportRequest = ExportQueryResultsRequest(database, statement, CSV(VERTICAL_BAR), dstPath)

    testExportToCsv(exportRequest, expectedValues = values.filter { (c1, _) -> c1 > "5" })
  }

  fun testExportTableToCsvFileDb() = testExportTableToCsv(createEmptyDatabase(DatabaseType.File))

  fun testExportTableToCsvLiveDb() = testExportTableToCsv(createEmptyDatabase(DatabaseType.Live))

  private fun testExportTableToCsv(database: SqliteDatabaseId) {
    val values = (1..9).toTwoColumnTable()
    fillDatabase(database, table1, values)

    val dstPath = tempDirTestFixture.toNioPath().resolve(outputFileName)
    val exportRequest = ExportTableRequest(database, table1, CSV(TAB), dstPath)

    testExportToCsv(exportRequest, expectedValues = values)
  }

  /** Overload suitable for single file CSV output (e.g. exporting a query or a single table). */
  private fun testExportToCsv(exportRequest: ExportRequest, expectedValues: TwoColumnTable) =
    testExportToCsv(
      exportRequest,
      expectedOutput = listOf(ExpectedOutputFile(exportRequest.dstPath, expectedValues)),
      decompress = { file -> listOf(file) } // no-op
    )

  /** Overload suitable for a general case (provide a [decompress] function if required to get the underlying CSV files). */
  private fun testExportToCsv(exportRequest: ExportRequest, expectedOutput: List<ExpectedOutputFile>, decompress: (Path) -> List<Path>) {
    // given: an export request
    // when: an export request is submitted
    submitExportRequest(exportRequest)

    // then: compare output file(s) with expected output
    verify(notifyExportComplete).invoke(exportRequest)
    verifyNoMoreInteractions(notifyExportComplete)

    val actualFiles = decompress(exportRequest.dstPath).sorted()
    actualFiles.zip(expectedOutput.sortedBy { it.path }) { actualPath, (expectedPath, expectedValues) ->
      assertThat(actualPath.toFile().canonicalPath).isEqualTo(expectedPath.toFile().canonicalPath)
      assertThat(actualPath.toLines()).isEqualTo(expectedValues.toCsvOutputLines(exportRequest.delimiter))
    }
  }

  fun testExportDatabaseToCsvFileDb() = testExportDatabaseToCsv(createEmptyDatabase(DatabaseType.File))

  fun testExportDatabaseToCsvLiveDb() = testExportDatabaseToCsv(createEmptyDatabase(DatabaseType.Live))

  private fun testExportDatabaseToCsv(database: SqliteDatabaseId) {
    // given: a database with a number of tables
    val tableValuePairs = listOf(
      table1 to (1..11).toTwoColumnTable(),
      table2 to (2..22).toTwoColumnTable(),
      table3 to (3..33).toTwoColumnTable()
    )
    tableValuePairs.forEach { (table, values) -> fillDatabase(database, table, values) }

    val dstPath = tempDirTestFixture.toNioPath().resolve("$outputFileName.zip")
    val exportRequest = ExportDatabaseRequest(database, CSV(COMMA), dstPath)

    val tmpDir = tempDirTestFixture.findOrCreateDir("unzipped")
    val decompress: (Path) -> List<Path> = { it.unzipTo(tmpDir.toNioPath()) }
    val expectedOutput = tableValuePairs.map { (table, values) -> ExpectedOutputFile(tmpDir.toNioPath().resolve("$table.csv"), values) }

    testExportToCsv(exportRequest, expectedOutput, decompress)
  }

  fun testInvalidRequestFileDb() = testInvalidRequest(DatabaseType.File)

  fun testInvalidRequestLiveDb() = testInvalidRequest(DatabaseType.Live)

  private fun testInvalidRequest(databaseType: DatabaseType) {
    // given: an invalid request
    val assertionDescription = "Expecting a SQLite exception caused by an invalid query."
    val exportRequest = ExportTableRequest(
      createEmptyDatabase(databaseType),
      "non-existing-table", // this will cause an exception (we are a database without any tables)
      CSV(TAB),
      tempDirTestFixture.createFile("ignored-output-file").toNioPath()
    )

    try {
      // when: a request is processed by the controller
      submitExportRequest(exportRequest)
      fail(assertionDescription) // if we got here, it means we didn't encounter the expected exception
    }
    catch (throwable: Throwable) {
      // then: expect the exception related to the issue
      val sqlException = generateSequence(throwable) { it.cause }.firstOrNull {
        it.message?.contains("no such table.*${exportRequest.srcTable}".toRegex()) ?: false
      }
      assertWithMessage(assertionDescription).that(sqlException).isNotNull()
    }
  }

  fun testNextConnectionId() {
    assertThat((1..5).map { nextConnectionId() }).isEqualTo((1..5).toList())
    assertThat((1..5).map { nextConnectionId() }).isEqualTo((6..10).toList())
  }

  private fun submitExportRequest(exportRequest: ExportRequest) =
    runDispatching { view.listeners.first().exportRequestSubmitted(exportRequest) }

  private fun createEmptyDatabase(type: DatabaseType): SqliteDatabaseId {
    val databaseFile = tempDirTestFixture.createFile(databaseFileName)

    val connection = when (type) {
      DatabaseType.File -> createFileDatabaseConnection(databaseFile)
      DatabaseType.Live -> CliDatabaseConnection(databaseFile.toNioPath(), sqliteCliClient, '|', taskExecutor)
    }

    val databaseId = when (type) {
      DatabaseType.File -> SqliteDatabaseId.fromFileDatabase(DatabaseFileData(databaseFile))
      DatabaseType.Live -> SqliteDatabaseId.fromLiveDatabase(databaseFile.toNioPath().toString(), nextConnectionId())
    }

    runDispatching { databaseRepository.addDatabaseConnection(databaseId, connection) }
    return databaseId
  }

  private fun fillDatabase(database: SqliteDatabaseId, tableName: String, values: TwoColumnTable) {
    database.execute("create table '$tableName' ('$column1' int, '$column2' text)")
    values.forEach { (v1, v2) -> database.execute("insert into '$tableName' values ('$v1', '$v2')") }
  }

  private fun SqliteDatabaseId.execute(statementText: String) = let { db ->
    val statement = createSqliteStatement(statementText)
    runDispatching { databaseRepository.executeStatement(db, statement).await() }
  }

  private fun createSqliteStatement(statement: String): SqliteStatement = runDispatching {
    withContext(edtExecutor.asCoroutineDispatcher()) {
      createSqliteStatement(project, statement)
    }
  }

  private fun createFileDatabaseConnection(databaseFile: VirtualFile): DatabaseConnection = runDispatching {
    getJdbcDatabaseConnection(testRootDisposable, databaseFile, FutureCallbackExecutor.wrap(taskExecutor)).await()
  }
}

private fun TempDirTestFixture.toNioPath() = File(tempDirPath).toPath()

private val ExportRequest.delimiter get(): Char = (format as CSV).delimiter.delimiter

private enum class DatabaseType { Live, File }

private data class ExpectedOutputFile(val path: Path, val values: TwoColumnTable)

private typealias TwoColumnTable = List<Pair<String, String>>

private fun TwoColumnTable.toCsvOutputLines(delimiter: Char): List<String> =
  listOf("$column1$delimiter$column2") + this.map { (v1, v2) -> "$v1$delimiter$v2" }

/** Two columns with increasing numbers (and a non-ascii suffix) */
private fun IntRange.toTwoColumnTable(): TwoColumnTable = this.map { "$it$nonAsciiSuffix" }.zipWithNext()