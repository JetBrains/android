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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
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
import com.android.tools.idea.sqlite.databaseConnection.SqliteResultSet
import com.android.tools.idea.sqlite.mocks.CliDatabaseConnection
import com.android.tools.idea.sqlite.mocks.FakeExportToFileDialogView
import com.android.tools.idea.sqlite.mocks.OpenDatabaseRepository
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.Delimiter.COMMA
import com.android.tools.idea.sqlite.model.Delimiter.SEMICOLON
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
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.ui.exportToFile.ExportInProgressViewImpl.UserCancellationException
import com.android.tools.idea.sqlite.utils.getJdbcDatabaseConnection
import com.android.tools.idea.sqlite.utils.initAdbFileProvider
import com.android.tools.idea.sqlite.utils.toLines
import com.android.tools.idea.sqlite.utils.unzipTo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.runDispatching
import com.google.common.base.Stopwatch
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Destination
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Outcome
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.Source
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportOperationCompletedEvent.SourceFormat
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createFile
import com.intellij.util.io.delete
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import com.intellij.util.io.size
import junit.framework.TestCase.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists

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

// TODO(161081452): add in-memory database test coverage
@Suppress("IncorrectParentDisposable")
@RunsInEdt
@RunWith(Parameterized::class)
class ExportToFileControllerTest(private val testConfig: TestConfig) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val testConfigurations = listOf(
      TestConfig(DatabaseType.File, targetFileAlreadyExists = true),
      TestConfig(DatabaseType.File, targetFileAlreadyExists = false),
      TestConfig(DatabaseType.Live, targetFileAlreadyExists = true),
      TestConfig(DatabaseType.Live, targetFileAlreadyExists = false)
    )
  }

  private val projectRule = AndroidProjectRule.onDisk()

  // We want to run tests on the EDT thread, but we also need to make sure the project rule is not
  // initialized on the EDT.
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /** Keeps connection ids unique */
  private val nextConnectionId: () -> Int = run { var next = 1; { next++ } }

  private lateinit var exportInProgressListener: (Job) -> Unit
  private lateinit var exportProcessedListener: ExportProcessedListener

  private lateinit var tempDirTestFixture: TempDirTestFixture
  private lateinit var databaseDownloadTestFixture: DatabaseDownloadTestFixture
  private lateinit var databaseLockingTestFixture: DatabaseLockingTestFixture

  private lateinit var edtExecutor: Executor
  private lateinit var taskExecutor: Executor

  private lateinit var databaseRepository: DatabaseRepository
  private lateinit var sqliteCliClient: SqliteCliClient

  private lateinit var analyticsTracker: DatabaseInspectorAnalyticsTracker
  private lateinit var view: FakeExportToFileDialogView
  private lateinit var controller: ExportToFileController

  private lateinit var project: Project
  private lateinit var testRootDisposable: Disposable

  @Before fun setUp() {
    project = projectRule.project
    testRootDisposable = projectRule.fixture.testRootDisposable

    exportInProgressListener = mock()
    exportProcessedListener = ExportProcessedListener()

    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    tempDirTestFixture.setUp()

    edtExecutor = EdtExecutorService.getInstance()
    taskExecutor = PooledThreadExecutor.INSTANCE

    databaseDownloadTestFixture = DatabaseDownloadTestFixture(tempDirTestFixture.toNioPath())
    databaseDownloadTestFixture.setUp()

    initAdbFileProvider(project)
    sqliteCliClient = SqliteCliClientImpl(SqliteCliProviderImpl(project).getSqliteCli()!!, taskExecutor.asCoroutineDispatcher())
    OpenDatabaseRepository(project, taskExecutor).let {
      databaseRepository = it
      databaseLockingTestFixture = DatabaseLockingTestFixture(it)
      databaseLockingTestFixture.setUp()
    }

    analyticsTracker = mock()
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, analyticsTracker)

    view = FakeExportToFileDialogView()
    controller = ExportToFileController(
      project,
      AndroidCoroutineScope(project, edtExecutor.asCoroutineDispatcher()),
      view,
      databaseRepository,
      databaseDownloadTestFixture::downloadDatabase,
      { databaseDownloadTestFixture.deleteDatabase(it) },
      { databaseLockingTestFixture.acquireDatabaseLock(it) },
      { databaseLockingTestFixture.releaseDatabaseLock(it) },
      taskExecutor,
      edtExecutor,
      exportInProgressListener,
      exportProcessedListener::onExportComplete,
      exportProcessedListener::onExportError
    )
    controller.setUp()
    controller.responseSizeByteLimitHint = 16 // 16 bytes - simulates scenarios where a query returns more rows than we allow in a batch
    Disposer.register(testRootDisposable, controller)
  }

  @After fun tearDown() {
    databaseDownloadTestFixture.tearDown()
    runDispatching { databaseRepository.clear() }
    tempDirTestFixture.tearDown()
    databaseLockingTestFixture.tearDown()
  }

  @Test fun testExportQueryToCsv() {
    val database = createEmptyDatabase(testConfig.databaseType)
    val values = populateDatabase(database, listOf(table1), listOf(view1)).single().content

    val statement = createSqliteStatement("select * from '$table1' where cast(\"$column1\" as text) > cast(5 as text)")
    val dstPath = tempDirTestFixture.toNioPath().resolve(outputFileName)
    val exportRequest = ExportQueryResultsRequest(database, statement, CSV(VERTICAL_BAR), dstPath)

    values.filter { (c1, _) -> c1 > "5" }.let { expectedValues ->
      assertThat(expectedValues).isNotEmpty()
      testExport(exportRequest, expectedValues.toCsvOutputLines(exportRequest.delimiter))
    }
  }

  @Test fun testExportTableToCsv() {
    val database = createEmptyDatabase(testConfig.databaseType)
    val values = populateDatabase(database, listOf(table1), listOf(view1)).single().content

    val dstPath = tempDirTestFixture.toNioPath().resolve(outputFileName)
    val exportRequest = ExportTableRequest(database, table1, CSV(TAB), dstPath)

    testExport(exportRequest, expectedValues = values.toCsvOutputLines(exportRequest.delimiter))
  }

  @Test fun testExportTableToSql() {
    val targetTable = table1
    testExportToSql(
      databaseType = testConfig.databaseType,
      databaseTables = listOf(table1, table2, table3),
      exportRequestCreator = { database, dstPath -> ExportTableRequest(database, targetTable, SQL, dstPath) },
      expectedTableNames = listOf(targetTable),
      expectedOutputDumpCommand = DumpTable(targetTable)
    )
  }

  @Test fun testExportDatabaseToSql() {
    val databaseTables = listOf(table1, table2, table3)
    testExportToSql(
      databaseType = testConfig.databaseType,
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
    verifyExportCallbacks: (durationMs: Long) -> Unit = { durationMs ->
      assertThat(exportProcessedListener.scenario).isEqualTo(SUCCESS)
      assertThat(exportProcessedListener.capturedRequest).isEqualTo(exportRequest)
      assertAnalyticsTrackerCall(analyticsTracker, exportRequest, durationMs, Outcome.SUCCESS_OUTCOME)
    }
  ) {
    // then: compare output file(s) with expected output
    val stopwatch = Stopwatch.createStarted()
    requireEmptyFileAtDestination(exportRequest.dstPath, testConfig.targetFileAlreadyExists)
    submitExportRequest(exportRequest)
    verify(exportInProgressListener).invoke(controller.lastExportJob!!)
    awaitExportComplete(15_000L)
    stopwatch.stop()

    verifyExportCallbacks(stopwatch.elapsed(MILLISECONDS))
    exportRequest.srcDatabase.let { db -> assertThat(databaseLockingTestFixture.wasLocked(db)).isEqualTo(db is LiveSqliteDatabaseId) }

    val actualFiles = decompress(exportRequest.dstPath).sorted()
    assertThat(actualFiles).isEqualTo(expectedOutput.map { it.path }.sorted())
    actualFiles.zip(expectedOutput.sortedBy { it.path }) { actualPath, (expectedPath, expectedValues) ->
      assertThat(actualPath.toFile().canonicalPath).isEqualTo(expectedPath.toFile().canonicalPath)
      assertThat(actualPath.toLines()).isEqualTo(expectedValues)
    }
  }

  /** Checks what was reported to analytics tracker after a <b>successful</b> export operation. */
  private fun assertAnalyticsTrackerCall(
    analyticsTracker: DatabaseInspectorAnalyticsTracker,
    exportRequest: ExportRequest,
    maxDurationMs: Long,
    expectedOutcome: Outcome
  ) {
    // Using captors below to go the opposite way than the prod code: from analytics values to export-request values.
    // Otherwise we'd end up with a copy of production code in the tests (which would be of questionable value).
    val sourceCaptor = ArgumentCaptor.forClass(Source::class.java)
    val sourceFormatCaptor = ArgumentCaptor.forClass(SourceFormat::class.java)
    val destinationCaptor = ArgumentCaptor.forClass(Destination::class.java)
    val durationMsCaptor = ArgumentCaptor.forClass(Int::class.java)
    val connectivityStateCaptor = ArgumentCaptor.forClass(ConnectivityState::class.java)
    val outcomeCaptor = ArgumentCaptor.forClass(Outcome::class.java)

    // `trackExportCompleted` does not accept null values and ArgumentCaptor for classes cannot work around that.
    // Using fallback values () below to work around it. These don't affect verifications.
    verify(analyticsTracker).trackExportCompleted(
      sourceCaptor.capture() ?: Source.UNKNOWN_SOURCE,
      sourceFormatCaptor.capture() ?: SourceFormat.UNKNOWN_SOURCE_FORMAT,
      destinationCaptor.capture() ?: Destination.UNKNOWN_DESTINATION,
      durationMsCaptor.capture(),
      outcomeCaptor.capture() ?: Outcome.UNKNOWN_OUTCOME,
      connectivityStateCaptor.capture() ?: ConnectivityState.UNKNOWN_CONNECTIVITY_STATE
    )

    when (sourceCaptor.allValues.single()) {
      Source.DATABASE_SOURCE -> assertThat(exportRequest).isInstanceOf(ExportDatabaseRequest::class.java)
      Source.TABLE_SOURCE -> assertThat(exportRequest).isInstanceOf(ExportTableRequest::class.java)
      Source.QUERY_SOURCE -> assertThat(exportRequest).isInstanceOf(ExportQueryResultsRequest::class.java)
      else -> fail()
    }

    when (sourceFormatCaptor.allValues.single()) {
      SourceFormat.FILE_FORMAT -> assertThat(exportRequest.srcDatabase.isInMemoryDatabase()).isFalse()
      SourceFormat.IN_MEMORY_FORMAT -> assertThat(exportRequest.srcDatabase.isInMemoryDatabase()).isTrue()
      else -> fail()
    }

    val format = when (exportRequest) {
      is ExportDatabaseRequest -> exportRequest.format
      is ExportTableRequest -> exportRequest.format
      is ExportQueryResultsRequest -> exportRequest.format
      else -> null
    }
    when (destinationCaptor.allValues.single()) {
      Destination.DB_DESTINATION -> {
        assertThat(format).isEqualTo(DB)
        assertThat(exportRequest::class.java).isEqualTo(ExportDatabaseRequest::class.java)
      }
      Destination.SQL_DESTINATION -> {
        assertThat(format).isEqualTo(SQL)
        assertThat(exportRequest::class.java).isAnyOf(ExportDatabaseRequest::class.java, ExportTableRequest::class.java)
      }
      Destination.CSV_DESTINATION -> {
        assertThat(format).isInstanceOf(CSV::class.java)
        assertThat(exportRequest::class.java).isAnyOf(
          ExportDatabaseRequest::class.java, ExportTableRequest::class.java, ExportQueryResultsRequest::class.java
        )
      }
      else -> fail()
    }

    durationMsCaptor.allValues.single().let { durationMs ->
      assertThat(durationMs).isGreaterThan(0)
      assertThat(durationMs).isAtMost(maxDurationMs.toInt())
    }

    when (connectivityStateCaptor.allValues.single()) {
      ConnectivityState.CONNECTIVITY_ONLINE -> assertThat(exportRequest.srcDatabase).isInstanceOf(LiveSqliteDatabaseId::class.java)
      ConnectivityState.CONNECTIVITY_OFFLINE -> assertThat(exportRequest.srcDatabase).isInstanceOf(FileSqliteDatabaseId::class.java)
      else -> fail()
    }

    assertThat(outcomeCaptor.allValues.single()).isEqualTo(expectedOutcome)
  }

  @Suppress("BlockingMethodInNonBlockingContext") // [CountDownLatch#await]
  @Test fun testExportCancelledByTheUser() {
    // set up a database
    val connection: DatabaseConnection = mock()

    // set up a database: prepare a 'freeze' on issuing a database query - making the export operation go indefinitely
    val queryIssuedLatch = CountDownLatch(1)
    whenever(connection.query(any())).thenAnswer {
      queryIssuedLatch.countDown()
      CoroutineScope(taskExecutor.asCoroutineDispatcher()).async<List<SqliteRow>> {
        CompletableDeferred<SqliteResultSet>().await() // never going to complete, giving us time to cancel the job
        fail() // we never expect to get past the above line
        mock()
      }.asListenableFuture()
    }

    val databaseId = SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("srcDb")))
    runDispatching { databaseRepository.addDatabaseConnection(databaseId, connection) }

    // submit export request
    val exportRequest = ExportTableRequest(databaseId, "ignored", CSV(SEMICOLON), tempDirTestFixture.toNioPath().resolve(outputFileName))
    val stopwatch = Stopwatch.createStarted()
    requireEmptyFileAtDestination(exportRequest.dstPath, testConfig.targetFileAlreadyExists)
    submitExportRequest(exportRequest)

    // verify that in-progress-listener (responsible for the progress bar) got called
    runDispatching { assertThat(queryIssuedLatch.await(5, SECONDS)).isTrue() }
    val job = controller.lastExportJob!!
    verify(exportInProgressListener).invoke(job)
    assertThat(job.isActive).isTrue()

    // cancel the job simulating the cancel button invoked by the user
    job.cancel(UserCancellationException())

    // verify the job gets cancelled and notifyError gets the confirmation
    awaitExportComplete(5000L)
    stopwatch.stop()
    assertThat(exportProcessedListener.scenario).isEqualTo(ERROR)
    assertThat(exportProcessedListener.capturedRequest).isEqualTo(exportRequest)
    assertThat(exportProcessedListener.capturedError).isInstanceOf(CancellationException::class.java)

    assertAnalyticsTrackerCall(analyticsTracker, exportRequest, stopwatch.elapsed(MILLISECONDS), Outcome.CANCELLED_BY_USER_OUTCOME)
  }

  @Test fun testExportDatabaseToCsv() {
    // given: a database with a number of tables
    val database = createEmptyDatabase(testConfig.databaseType)
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

  @Test fun testExportDatabaseToDb() {
    // given: a database
    val database = createEmptyDatabase(testConfig.databaseType)
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

  @Test fun testInvalidRequest() {
    // given: an invalid request
    val exportRequest = ExportTableRequest(
      createEmptyDatabase(testConfig.databaseType),
      "non-existing-table", // this will cause an exception (we are a database without any tables)
      CSV(TAB),
      tempDirTestFixture.createFile("ignored-output-file").toNioPath()
    )

    // when/then
    val stopwatch = Stopwatch.createStarted()
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
        stopwatch.stop()
        assertAnalyticsTrackerCall(analyticsTracker, exportRequest, stopwatch.elapsed(MILLISECONDS), Outcome.ERROR_OUTCOME)
      }
    )
  }

  @Test fun testNextConnectionId() {
    assertThat((1..5).map { nextConnectionId() }).isEqualTo((1..5).toList())
    assertThat((1..5).map { nextConnectionId() }).isEqualTo((6..10).toList())
  }

  private fun submitExportRequest(exportRequest: ExportRequest) =
    runDispatching { view.listeners.forEach { it.exportRequestSubmitted(exportRequest) } }

  /**
   * By enforcing an empty file at destination (if [shouldExist]) we prevent files from previous test runs from causing a false positive
   * test outcome.
   */
  private fun requireEmptyFileAtDestination(path: Path, shouldExist: Boolean) {
    // Directory case is unusual, and better to fail than accidentally delete too much data.
    assertWithMessage("Export target ($path) is an existing directory. Expecting a file or a new path.").that(path.isDirectory()).isFalse()

    when {
      path.exists() -> when {
        !shouldExist -> path.delete()
        shouldExist && path.size() > 0 -> {
          path.delete()
          path.createFile()
        }
      }
      shouldExist -> path.createFile()
    }

    assertThat(path.exists()).isEqualTo(shouldExist)
    if (shouldExist) {
      assertThat(path.isFile()).isTrue()
      assertThat(path.size()).isEqualTo(0)
    }
  }

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

  enum class DatabaseType { Live, File }

  data class TestConfig(val databaseType: DatabaseType, val targetFileAlreadyExists: Boolean)
}

private fun SqliteCliResponse.checkSuccess(): SqliteCliResponse = apply {
  assertThat(this.exitCode).isEqualTo(0)
}

private fun TempDirTestFixture.toNioPath() = File(tempDirPath).toPath()

private val ExportRequest.delimiter get(): Char = (format as CSV).delimiter.delimiter

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

/**
 * Simulates database locking.
 *
 * Additionally, ensures that:
 * - locks are only requested for [LiveSqliteDatabaseId]s,
 * - [releaseDatabaseLock] only accepts locks issued by [acquireDatabaseLock],
 * - a lock can only be released once,
 * - all locks are released by the time [tearDown] is called.
 */
private class DatabaseLockingTestFixture(private val databaseRepository: OpenDatabaseRepository) : IdeaTestFixture {
  private lateinit var nextLockId: AtomicInteger
  private lateinit var lockIdToDatabase: ConcurrentHashMap<Int, SqliteDatabaseId>
  private lateinit var lockHistory: ConcurrentHashMap<SqliteDatabaseId, Unit> // using the map as a set

  override fun setUp() {
    nextLockId = AtomicInteger(1)
    lockIdToDatabase = ConcurrentHashMap()
    lockHistory = ConcurrentHashMap()
  }

  override fun tearDown() {
    assertThat(lockIdToDatabase.isEmpty())
  }

  fun acquireDatabaseLock(databaseId: Int): Int {
    val db = databaseRepository.openDatabases.filterIsInstance<LiveSqliteDatabaseId>().single { it.connectionId == databaseId }
    val lock = nextLockId.getAndIncrement()
    lockIdToDatabase.put(lock, db) ?: return lock
    throw IllegalStateException()
  }

  fun releaseDatabaseLock(lockId: Int) {
    val db = lockIdToDatabase.remove(lockId) ?: throw IllegalStateException()
    lockHistory[db] = Unit // presence of the key in the map is sufficient to indicate that the db was locked
  }

  fun wasLocked(db: SqliteDatabaseId): Boolean = lockHistory.containsKey(db)
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
