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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.OfflineModeManager.DownloadProgress
import com.android.tools.idea.sqlite.OfflineModeManager.DownloadState.COMPLETED
import com.android.tools.idea.sqlite.OfflineModeManager.DownloadState.IN_PROGRESS
import com.android.tools.idea.sqlite.cli.SqliteCliArg
import com.android.tools.idea.sqlite.cli.SqliteCliArgs
import com.android.tools.idea.sqlite.cli.SqliteCliClient
import com.android.tools.idea.sqlite.cli.SqliteCliClientImpl
import com.android.tools.idea.sqlite.cli.SqliteCliProviderImpl
import com.android.tools.idea.sqlite.cli.SqliteCliResponse
import com.android.tools.idea.sqlite.controllers.DumpCommand.DumpDatabase
import com.android.tools.idea.sqlite.controllers.DumpCommand.DumpTable
import com.android.tools.idea.sqlite.controllers.ExportProcessedListener.Scenario.ERROR
import com.android.tools.idea.sqlite.controllers.ExportProcessedListener.Scenario.NOT_CALLED
import com.android.tools.idea.sqlite.controllers.ExportProcessedListener.Scenario.SUCCESS
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.mocks.CliDatabaseConnection
import com.android.tools.idea.sqlite.mocks.FakeExportToFileDialogView
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.Delimiter.COMMA
import com.android.tools.idea.sqlite.model.Delimiter.TAB
import com.android.tools.idea.sqlite.model.Delimiter.VERTICAL_BAR
import com.android.tools.idea.sqlite.model.ExportFormat.CSV
import com.android.tools.idea.sqlite.model.ExportFormat.DB
import com.android.tools.idea.sqlite.model.ExportFormat.SQL
import com.android.tools.idea.sqlite.model.ExportRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportDatabaseRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportQueryResultsRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportTableRequest
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.FileSqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.LiveSqliteDatabaseId
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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createFile
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.ide.PooledThreadExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.Executor

private const val nonAsciiSuffix = " ąę"
private const val table1 = "t1$nonAsciiSuffix"
private const val table2 = "t2$nonAsciiSuffix"
private const val table3 = "t3$nonAsciiSuffix"
private const val view1 = "v1$nonAsciiSuffix"
private const val view2 = "v2$nonAsciiSuffix"
private const val column1 = "c1$nonAsciiSuffix"
private const val column2 = "c2$nonAsciiSuffix"
private const val databaseDir = "db-dir-$nonAsciiSuffix"
private const val databaseFileName = "db$nonAsciiSuffix.db"
private const val outputFileName = "output$nonAsciiSuffix.out"
private const val downloadFolderName = "downloaded$nonAsciiSuffix"

/** Keeps connection ids unique */
private val nextConnectionId: () -> Int = run { var next = 1; { next++ } }

// TODO(161081452): add in-memory database test coverage
@Suppress("IncorrectParentDisposable")
class ExportToFileControllerTest : LightPlatformTestCase() {
  private lateinit var exportProcessedListener: ExportProcessedListener

  private lateinit var tempDirTestFixture: TempDirTestFixture
  private lateinit var databaseDownloadTestFixture: DatabaseDownloadTestFixture

  private lateinit var edtExecutor: Executor
  private lateinit var taskExecutor: Executor

  private lateinit var databaseRepository: DatabaseRepository
  private lateinit var sqliteCliClient: SqliteCliClient

  private lateinit var view: FakeExportToFileDialogView
  private lateinit var controller: ExportToFileController

  override fun setUp() {
    super.setUp()

    exportProcessedListener = ExportProcessedListener()

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()

    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = PooledThreadExecutor.INSTANCE

    databaseDownloadTestFixture = DatabaseDownloadTestFixture(tempDirTestFixture.toNioPath())
    databaseDownloadTestFixture.setUp()

    initAdbFileProvider(project)
    sqliteCliClient = SqliteCliClientImpl(SqliteCliProviderImpl(project).getSqliteCli()!!, taskExecutor.asCoroutineDispatcher())
    databaseRepository = OpenDatabaseRepository(project, taskExecutor)

    view = FakeExportToFileDialogView()
    controller = ExportToFileController(
      project,
      AndroidCoroutineScope(project, edtExecutor.asCoroutineDispatcher()),
      view,
      databaseRepository,
      databaseDownloadTestFixture::downloadDatabase,
      { databaseDownloadTestFixture.deleteDatabase(it) },
      taskExecutor,
      edtExecutor,
      exportProcessedListener::onExportComplete,
      exportProcessedListener::onExportError
    )
    controller.setUp()
    Disposer.register(testRootDisposable, controller)
  }

  override fun tearDown() {
    databaseDownloadTestFixture.tearDown()
    runDispatching { databaseRepository.clear() }
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  fun testExportQueryToCsvFileDb() = testExportQueryToCsv(DatabaseType.File)

  fun testExportQueryToCsvLiveDb() = testExportQueryToCsv(DatabaseType.Live)

  private fun testExportQueryToCsv(databaseType: DatabaseType) {
    val database = createEmptyDatabase(databaseType)
    val values = populateDatabase(database, listOf(table1), listOf(view1)).single().content

    val statement = createSqliteStatement("select * from '$table1' where cast(\"$column1\" as text) > cast(5 as text)")
    val dstPath = tempDirTestFixture.toNioPath().resolve(outputFileName)
    val exportRequest = ExportQueryResultsRequest(database, statement, CSV(VERTICAL_BAR), dstPath)

    values.filter { (c1, _) -> c1 > "5" }.let { expectedValues ->
      assertThat(expectedValues).isNotEmpty()
      testExport(exportRequest, expectedValues.toCsvOutputLines(exportRequest.delimiter))
    }
  }

  fun testExportTableToCsvFileDb() = testExportTableToCsv(DatabaseType.File)

  fun testExportTableToCsvLiveDb() = testExportTableToCsv(DatabaseType.Live)

  private fun testExportTableToCsv(databaseType: DatabaseType) {
    val database = createEmptyDatabase(databaseType)
    val values = populateDatabase(database, listOf(table1), listOf(view1)).single().content

    val dstPath = tempDirTestFixture.toNioPath().resolve(outputFileName)
    val exportRequest = ExportTableRequest(database, table1, CSV(TAB), dstPath)

    testExport(exportRequest, expectedValues = values.toCsvOutputLines(exportRequest.delimiter))
  }

  fun testExportTableToSqlFileDb() = testExportTableToSql(DatabaseType.File)

  fun testExportTableToSqlLiveDb() = testExportTableToSql(DatabaseType.Live)

  private fun testExportTableToSql(databaseType: DatabaseType) {
    val targetTable = table1
    testExportToSql(
      databaseType = databaseType,
      databaseTables = listOf(table1, table2, table3),
      exportRequestCreator = { database, dstPath -> ExportTableRequest(database, targetTable, SQL, dstPath) },
      expectedTableNames = listOf(targetTable),
      expectedOutputDumpCommand = DumpTable(targetTable)
    )
  }

  fun testExportDatabaseToSqlFileDb() = testExportDatabaseToSql(DatabaseType.File)

  fun testExportDatabaseToSqlLiveDb() = testExportDatabaseToSql(DatabaseType.Live)

  private fun testExportDatabaseToSql(databaseType: DatabaseType) {
    val databaseTables = listOf(table1, table2, table3)
    testExportToSql(
      databaseType = databaseType,
      databaseTables = databaseTables,
      exportRequestCreator = { database, dstPath -> ExportDatabaseRequest(database, SQL, dstPath) },
      expectedTableNames = databaseTables,
      expectedOutputDumpCommand = DumpDatabase
    )
  }

  private fun testExportToSql(
    databaseType: DatabaseType,
    databaseTables: List<String>,
    exportRequestCreator: (SqliteDatabaseId, dstPath: Path) -> ExportRequest,
    expectedTableNames: List<String>,
    expectedOutputDumpCommand: DumpCommand
  ) {
    val database = createEmptyDatabase(databaseType)
    populateDatabase(database, databaseTables, listOf(view1, view2)).map { it.name }

    val dstPath = tempDirTestFixture.toNioPath().resolve("$outputFileName.sql")
    val exportRequest = exportRequestCreator(database, dstPath)

    val expectedOutput = runSqlite3Command(
      SqliteCliArgs
        .builder()
        .database(database.backingFile)
        .apply { expectedOutputDumpCommand.setOnBuilder(this) }
        .build()
    ).checkSuccess().stdOutput.split(System.lineSeparator())
      .also { lines ->
        assertThat(lines).isNotEmpty()
        expectedTableNames.forEach { tableName ->
          assertThat(lines.filter { it.contains("create table ".toRegex(RegexOption.IGNORE_CASE)) }).hasSize(expectedTableNames.size)
          assertThat(lines.filter { it.contains("create table .*$tableName".toRegex(RegexOption.IGNORE_CASE)) }).isNotEmpty()
          assertThat(lines.filter { it.contains("insert into .*$tableName".toRegex(RegexOption.IGNORE_CASE)) }).isNotEmpty()
        }
      }

    testExport(exportRequest, expectedOutput)
  }

  /** Overload suitable for single file output (e.g. exporting a query or a single table). */
  private fun testExport(exportRequest: ExportRequest, expectedValues: List<String>) =
    testExport(
      exportRequest,
      decompress = { file -> listOf(file) },
      expectedOutput = listOf(ExpectedOutputFile(exportRequest.dstPath, expectedValues)) // no-op
    )

  /** Overload suitable for a general case (provide a [decompress] function if required to get the underlying output files). */
  private fun testExport(
    exportRequest: ExportRequest,
    decompress: (Path) -> List<Path>,
    expectedOutput: List<ExpectedOutputFile>,
    verifyExportCallbacks: () -> Unit = {
      assertThat(exportProcessedListener.scenario).isEqualTo(SUCCESS)
      assertThat(exportProcessedListener.capturedRequest).isEqualTo(exportRequest)
    }
  ) {
    // then: compare output file(s) with expected output
    submitExportRequest(exportRequest)
    awaitExportComplete(5000L)

    verifyExportCallbacks()

    val actualFiles = decompress(exportRequest.dstPath).sorted()
    assertThat(actualFiles).isEqualTo(expectedOutput.map { it.path }.sorted())
    actualFiles.zip(expectedOutput.sortedBy { it.path }) { actualPath, (expectedPath, expectedValues) ->
      assertThat(actualPath.toFile().canonicalPath).isEqualTo(expectedPath.toFile().canonicalPath)
      assertThat(actualPath.toLines()).isEqualTo(expectedValues)
    }
  }

  fun testExportDatabaseToCsvFileDb() = testExportDatabaseToCsv(DatabaseType.File)

  fun testExportDatabaseToCsvLiveDb() = testExportDatabaseToCsv(DatabaseType.Live)

  private fun testExportDatabaseToCsv(databaseType: DatabaseType) {
    // given: a database with a number of tables
    val database = createEmptyDatabase(databaseType)
    val tableValuePairs = populateDatabase(database, listOf(table1, table2, table3), listOf(view1, view2))

    val dstPath = tempDirTestFixture.toNioPath().resolve("$outputFileName.zip")
    val exportRequest = ExportDatabaseRequest(database, CSV(COMMA), dstPath)

    val tmpDir = tempDirTestFixture.findOrCreateDir("unzipped")
    val decompress: (Path) -> List<Path> = { it.unzipTo(tmpDir.toNioPath()) }
    val expectedOutput = tableValuePairs.map { (table, values) ->
      ExpectedOutputFile(tmpDir.toNioPath().resolve("$table.csv"), values.toCsvOutputLines(exportRequest.delimiter))
    }

    testExport(exportRequest, decompress, expectedOutput)
  }

  fun testExportDatabaseToDbLiveDb() = testExportDatabaseToDb(DatabaseType.Live)

  fun testExportDatabaseToDbFileDb() = testExportDatabaseToDb(DatabaseType.File)

  private fun testExportDatabaseToDb(databaseType: DatabaseType) {
    // given: a database
    val database = createEmptyDatabase(databaseType)
    val expectedTables = listOf(table1, table2, table3)
    val expectedViews = listOf(view1, view2)
    populateDatabase(database, expectedTables, expectedViews)

    // given: an export request
    val exportRequest = let {
      val dstPath = tempDirTestFixture.findOrCreateDir("destination-dir").toNioPath().resolve("$outputFileName.db")
      ExportDatabaseRequest(database, DB, dstPath)
    }

    // given: a set of expected outputs

    val (actualTablesPath, actualViewsPath, actualSchemaPath) = tempDirTestFixture.findOrCreateDir("db-as-txt").toNioPath().let { dir ->
      listOf("actual-tables.txt", "actual-views.txt", "actual-schema.txt").map { dir.resolve(it) }
    }

    val databaseToTextFiles: (Path) -> List<Path> = { path ->
      runSqlite3Command(SqliteCliArgs.builder().database(path).output(actualTablesPath).queryTableList().build()).checkSuccess()
      runSqlite3Command(SqliteCliArgs.builder().database(path).output(actualViewsPath).queryViewList().build()).checkSuccess()
      runSqlite3Command(SqliteCliArgs.builder().database(path).output(actualSchemaPath).dump().build()).checkSuccess()
      listOf(actualSchemaPath, actualTablesPath, actualViewsPath)
    }

    val expectedSchema = runSqlite3Command(
      SqliteCliArgs
        .builder()
        .database(database.backingFile)
        .dump()
        .build()
    ).checkSuccess().stdOutput.split(System.lineSeparator())

    val expected: List<ExpectedOutputFile> = listOf(
      ExpectedOutputFile(actualTablesPath, expectedTables),
      ExpectedOutputFile(actualViewsPath, expectedViews),
      ExpectedOutputFile(actualSchemaPath, expectedSchema)
    )

    // when/then:
    testExport(exportRequest, databaseToTextFiles, expected)
  }

  private val SqliteDatabaseId.backingFile: Path
    get() = when (this) {
      is FileSqliteDatabaseId -> databaseFileData.mainFile.toNioPath()
      is LiveSqliteDatabaseId -> Paths.get(path) // we use the fact that in the test setup, live db is backed by a local file
    }

  fun testInvalidRequestFileDb() = testInvalidRequest(DatabaseType.File)

  fun testInvalidRequestLiveDb() = testInvalidRequest(DatabaseType.Live)

  private fun testInvalidRequest(databaseType: DatabaseType) {
    // given: an invalid request
    val exportRequest = ExportTableRequest(
      createEmptyDatabase(databaseType),
      "non-existing-table", // this will cause an exception (we are a database without any tables)
      CSV(TAB),
      tempDirTestFixture.createFile("ignored-output-file").toNioPath()
    )

    // when/then
    testExport(
      exportRequest = exportRequest,
      decompress = {
        // assertThat(exportRequest.dstPath.exists()).isFalse() // TODO(161081452): don't leave empty files around on error
        emptyList()  // no output expected
      },
      expectedOutput = emptyList(), // no output expected
      verifyExportCallbacks = {
        assertThat(exportProcessedListener.scenario).isEqualTo(ERROR)
        assertThat(exportProcessedListener.capturedRequest).isEqualTo(exportRequest)
        val sqlException = generateSequence(exportProcessedListener.capturedError) { it.cause }.firstOrNull {
          it.message?.contains("no such table.*${exportRequest.srcTable}".toRegex()) ?: false
        }
        assertWithMessage("Expecting a SQLite exception caused by an invalid query.").that(sqlException).isNotNull()
      }
    )
  }

  fun testNextConnectionId() {
    assertThat((1..5).map { nextConnectionId() }).isEqualTo((1..5).toList())
    assertThat((1..5).map { nextConnectionId() }).isEqualTo((6..10).toList())
  }

  private fun submitExportRequest(exportRequest: ExportRequest) =
    runDispatching { view.listeners.forEach { it.exportRequestSubmitted(exportRequest) } }

  @Suppress("SameParameterValue")
  private fun awaitExportComplete(timeoutMs: Long) = runDispatching {
    withTimeout(timeoutMs) { controller.lastExportJob!!.join() }
  }

  private fun createEmptyDatabase(type: DatabaseType): SqliteDatabaseId {
    val databaseFile = let {
      val databaseDir = tempDirTestFixture.findOrCreateDir(databaseDir)
      databaseDir.createChildFile(databaseFileName)
    }

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

  private fun populateDatabase(database: SqliteDatabaseId, tableNames: List<String>, viewNames: List<String>): List<Table> {
    fun createTable(database: SqliteDatabaseId, table: Table) {
      database.execute("create table '${table.name}' ('$column1' int, '$column2' text)")
      table.content.forEach { (v1, v2) -> database.execute("insert into '${table.name}' values ('$v1', '$v2')") }
    }

    val tableValuePairs = tableNames.mapIndexed { ix, tableName ->
      val first = ix + 1
      val last = first * 11
      Table(tableName, (first..last).toTwoColumnTable())
    }
    tableValuePairs.forEach { createTable(database, it) }

    viewNames.forEach { viewName ->
      database.execute("create view '$viewName' as select * from '${tableNames.first()}'") // to verify if views also get exported
    }

    return tableValuePairs
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

  private fun runSqlite3Command(args: List<SqliteCliArg>): SqliteCliResponse = runDispatching {
    withContext(taskExecutor.asCoroutineDispatcher()) {
      sqliteCliClient.runSqliteCliCommand(args)
    }
  }
}

private fun SqliteCliResponse.checkSuccess(): SqliteCliResponse = apply {
  assertThat(this.exitCode).isEqualTo(0)
}

private fun TempDirTestFixture.toNioPath() = File(tempDirPath).toPath()

private val ExportRequest.delimiter get(): Char = (format as CSV).delimiter.delimiter

private enum class DatabaseType { Live, File }

private sealed class DumpCommand(open val setOnBuilder: (SqliteCliArgs.Builder) -> Unit) {
  object DumpDatabase : DumpCommand({ it.dump() })
  data class DumpTable(val name: String) : DumpCommand({ it.dumpTable(name) })
}

private data class ExpectedOutputFile(val path: Path, val values: List<String>)

private typealias TwoColumnTable = List<Pair<String, String>>

private data class Table(val name: String, val content: TwoColumnTable)

private fun TwoColumnTable.toCsvOutputLines(delimiter: Char): List<String> =
  listOf("$column1$delimiter$column2") + this.map { (v1, v2) -> "$v1$delimiter$v2" }

/** Two columns with increasing numbers (and a non-ascii suffix) */
private fun IntRange.toTwoColumnTable(): TwoColumnTable = this.map { "$it$nonAsciiSuffix" }.zipWithNext()

private fun VirtualFile.createChildFile(name: String): VirtualFile {
  if (!isDirectory) throw IllegalStateException("Parent needs to be a directory. Got: $this.")
  if (findChild(name) != null) throw IllegalStateException("Child already exists.")
  return runWriteAction { createChildData(null, name) }
}

/** Simulates downloading a [LiveSqliteDatabaseId] */
private class DatabaseDownloadTestFixture(private val tmpDir: Path) : IdeaTestFixture {
  private lateinit var downloadFolder: Path
  private lateinit var downloaded: MutableList<DatabaseFileData>
  private lateinit var deleted: MutableList<DatabaseFileData>

  override fun setUp() {
    downloadFolder = tmpDir.resolve(downloadFolderName).createDirectories()
    downloaded = mutableListOf()
    deleted = mutableListOf()
  }

  override fun tearDown() {
    val sortKey = { fileData: DatabaseFileData -> fileData.mainFile.path }
    assertThat(deleted.sortedBy(sortKey)).isEqualTo(downloaded.sortedBy(sortKey))
  }

  fun downloadDatabase(db: LiveSqliteDatabaseId, handleError: (String, Throwable?) -> Unit): Flow<DownloadProgress> =
    flow {
      try {
        val downloadedDatabase = createDatabaseCopy(db)
        downloaded.add(downloadedDatabase)
        emit(DownloadProgress(IN_PROGRESS, listOf(downloadedDatabase), 1))
        emit(DownloadProgress(IN_PROGRESS, listOf(downloadedDatabase), 1))
        emit(DownloadProgress(COMPLETED, listOf(downloadedDatabase), 1))
      }
      catch (t: Throwable) {
        handleError("Error while downloading a database: ${db.name}", t)
      }
    }

  fun deleteDatabase(file: DatabaseFileData) {
    deleted.add(file)
  }

  private fun createDatabaseCopy(db: LiveSqliteDatabaseId): DatabaseFileData {
    val src = Paths.get(db.path) // in test setup the database will already be on disk (i.e. not on a device)
    val dbFileName = src.fileName.toString()

    val mainFile = createFile(dbFileName)
    val wal1 = createFile("$dbFileName.wal1") // empty WAL
    val wal2 = createFile("$dbFileName.wal2") // empty WAL
    Files.copy(src, mainFile, REPLACE_EXISTING)
    return DatabaseFileData(mainFile.toVirtualFile(), listOf(wal1, wal2).map { it.toVirtualFile() })
  }

  private fun createFile(dbFileName: String): Path = downloadFolder.resolve(dbFileName).also { it.createFile() }

  private fun Path.toVirtualFile(): VirtualFile = VfsUtil.findFile(this, true)!!
}

/** Allows to track the outcome of an [ExportRequest] submitted to an [ExportToFileController]. */
private class ExportProcessedListener {
  var capturedRequest: ExportRequest? = null
  var capturedError: Throwable? = null
  var scenario: Scenario = NOT_CALLED

  fun onExportComplete(request: ExportRequest) {
    checkOnlyCall()
    capturedRequest = request
    scenario = SUCCESS
  }

  fun onExportError(request: ExportRequest, error: Throwable?) {
    checkOnlyCall()
    capturedRequest = request
    capturedError = error
    scenario = ERROR
  }

  private fun checkOnlyCall() {
    if (scenario != NOT_CALLED) throw IllegalStateException("Expected: a single call to a callback method. Actual: more than one call.")
  }

  enum class Scenario { NOT_CALLED, SUCCESS, ERROR }
}
