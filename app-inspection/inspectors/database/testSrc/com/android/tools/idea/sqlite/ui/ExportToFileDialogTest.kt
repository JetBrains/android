/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.Delimiter
import com.android.tools.idea.sqlite.model.Delimiter.COMMA
import com.android.tools.idea.sqlite.model.Delimiter.SEMICOLON
import com.android.tools.idea.sqlite.model.Delimiter.SPACE
import com.android.tools.idea.sqlite.model.Delimiter.TAB
import com.android.tools.idea.sqlite.model.Delimiter.VERTICAL_BAR
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportDatabaseDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportQueryResultsDialogParams
import com.android.tools.idea.sqlite.model.ExportDialogParams.ExportTableDialogParams
import com.android.tools.idea.sqlite.model.ExportFormat
import com.android.tools.idea.sqlite.model.ExportFormat.CSV
import com.android.tools.idea.sqlite.model.ExportFormat.DB
import com.android.tools.idea.sqlite.model.ExportFormat.SQL
import com.android.tools.idea.sqlite.model.ExportRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportDatabaseRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportQueryResultsRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportTableRequest
import com.android.tools.idea.sqlite.model.SqliteAffinity
import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteStatementType.SELECT
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogViewImpl
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.SystemProperties.getUserHome
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections.singletonList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JRadioButton
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

private val EXPORT_FORMATS_ALL = listOf(DB, SQL, CSV(mock()))

/** Test suite verifying Export-to-File Dialog behaviour */
@Suppress("NestedLambdaShadowedImplicitParameter", "SameParameterValue")
class ExportToFileDialogTest : LightPlatformTestCase() {
  private val inFileDatabaseId =
    SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("name")))
  private val inMemoryDatabaseId = SqliteDatabaseId.fromLiveDatabase(":memory: database1337", 1337)
  private val databaseId =
    inFileDatabaseId // for cases where it does not matter if the database is file-backed or
  // memory-backed
  private val column1 =
    SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
  private val column2 =
    SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
  private val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
  private val query =
    SqliteStatement(
      SELECT,
      "select * from ${table1.name} where ${table1.columns.first().name} == qwerty"
    )
  private val analyticsTracker = mock<DatabaseInspectorAnalyticsTracker>()

  override fun setUp() {
    super.setUp()
    assertThat(inFileDatabaseId.isInMemoryDatabase()).isFalse()
    assertThat(inMemoryDatabaseId.isInMemoryDatabase()).isTrue()
    enableHeadlessDialogs(testRootDisposable)
    project.registerServiceInstance(DatabaseInspectorAnalyticsTracker::class.java, analyticsTracker)
  }

  fun test_availableUiElements_exportDatabase_inFile() {
    test_availableUiElements_exportDatabase(inFileDatabaseId, EXPORT_FORMATS_ALL)
  }

  fun test_availableUiElements_exportDatabase_inMemory() {
    test_availableUiElements_exportDatabase(inMemoryDatabaseId, listOf(CSV(mock())))
  }

  private fun test_availableUiElements_exportDatabase(
    databaseId: SqliteDatabaseId,
    expectedExportFormats: List<ExportFormat>
  ) {
    test_availableUiElements(
      params = ExportDatabaseDialogParams(databaseId, Origin.UNKNOWN_ORIGIN),
      expectedTitle = "Export Database",
      expectedExportFormats = expectedExportFormats
    )
  }

  fun test_availableUiElements_exportTable_inFile() {
    test_availableUiElements_exportTable(inFileDatabaseId, listOf(SQL, CSV(mock())))
  }

  fun test_availableUiElements_exportTable_inMemory() {
    test_availableUiElements_exportTable(inMemoryDatabaseId, listOf(CSV(mock())))
  }

  private fun test_availableUiElements_exportTable(
    databaseId: SqliteDatabaseId,
    expectedExportFormats: List<ExportFormat>
  ) {
    test_availableUiElements(
      params = ExportTableDialogParams(databaseId, table1.name, Origin.SCHEMA_TREE_CONTEXT_MENU),
      expectedTitle = "Export Table",
      expectedExportFormats = expectedExportFormats
    )
  }

  fun test_availableUiElements_exportQueryResults_inFile() {
    test_availableUiElements_exportQueryResults(inFileDatabaseId)
  }

  fun test_availableUiElements_exportQueryResults_inMemory() {
    test_availableUiElements_exportQueryResults(inMemoryDatabaseId)
  }

  private fun test_availableUiElements_exportQueryResults(databaseId: SqliteDatabaseId) {
    test_availableUiElements(
      params = ExportQueryResultsDialogParams(databaseId, query, Origin.SCHEMA_TREE_EXPORT_BUTTON),
      expectedTitle = "Export Query Results",
      expectedExportFormats = listOf(CSV(mock()))
    )
  }

  private fun test_availableUiElements(
    params: ExportDialogParams,
    expectedTitle: String,
    expectedExportFormats: List<ExportFormat>
  ) {
    val dialog = ExportToFileDialogViewImpl(project, params)
    val dialogListener = mock<ExportToFileDialogView.Listener>()
    dialog.addListener(dialogListener)

    createModalDialogAndInteractWithIt({ dialog.show() }) {
      verify(analyticsTracker).trackExportDialogOpened(params.actionOrigin)

      // check dialog title
      assertThat(it.title).isEqualTo(expectedTitle)

      // check available formats (all format buttons are created, but only relevant ones are shown,
      // which greatly simplifies the UI code)
      val treeWalker = TreeWalker(it.rootPane)
      val formatButtons = treeWalker.formatButtons()
      assertThat(formatButtons.map { it.text })
        .containsExactlyElementsIn(EXPORT_FORMATS_ALL.map { it.displayName })
      formatButtons.forEach { button ->
        val isSupported = expectedExportFormats.any { it.displayName == button.text }
        assertThat(button.isVisible).isEqualTo(isSupported)
        assertThat(button.isEnabled).isEqualTo(isSupported)
      }

      // ensure delimiter only enabled in CSV option
      // ensure suggested destination path has correct extension
      val delimiterComboBox = treeWalker.delimiterComboBox()
      val destinationPathTextField = treeWalker.destinationPathTextField()
      expectedExportFormats.forEach { format ->
        formatButtons.single { it.text == format.displayName }.doClick()
        val isSupported = format is CSV
        assertThat(delimiterComboBox.isEnabled).isEqualTo(isSupported)
        assertThat(destinationPathTextField.text)
          .endsWith(".${expectedFileExtension(format, params)}")
      }

      // ensure all delimiter options are available
      assertThat(delimiterComboBox.items)
        .containsExactlyElementsIn(Delimiter.values().map { it.displayName })

      treeWalker.actionButton("Cancel").doClick()
    }

    assertThat(dialog.exitCode).isEqualTo(CANCEL_EXIT_CODE)
    verifyNoMoreInteractions(dialogListener)
  }

  fun test_dstPathValidation_validPath() = test_dstPathValidation("/out.zip", true)

  fun test_dstPathValidation_validPathLonger() =
    test_dstPathValidation(Paths.get(getUserHome(), "out.zip").toString(), true)

  fun test_dstPathValidation_validPathTilde() = test_dstPathValidation("~/out.zip", true)

  fun test_dstPathValidation_emptyPath() = test_dstPathValidation("", false)

  fun test_dstPathValidation_blankPath() = test_dstPathValidation("   ", false)

  fun test_dstPathValidation_noParentDir() = test_dstPathValidation("out.zip", false)

  fun test_dstPathValidation_targetIsExistingDir() = test_dstPathValidation(getUserHome(), false)

  fun test_dstPathValidation_multiplePaths() =
    test_dstPathValidation(
      listOf(
        PathTestCase("", false),
        PathTestCase(" ", false),
        PathTestCase("/", false),
        PathTestCase("/out", true),
        PathTestCase("/out/", false),
        PathTestCase("/does-not-exist-path", true),
        PathTestCase("/does-not-exist-path/", false),
        PathTestCase("/does-not-exist-path/out", false),
        PathTestCase(Paths.get(getUserHome(), "out.zip").toString(), true),
        PathTestCase("", false),
      )
    )

  private data class PathTestCase(val path: String, val isValidPath: Boolean)

  private fun test_dstPathValidation(path: String, isValidPath: Boolean) {
    test_dstPathValidation(singletonList(PathTestCase(path, isValidPath)))
  }

  private fun test_dstPathValidation(paths: List<PathTestCase>) {
    val params = ExportDatabaseDialogParams(inFileDatabaseId, Origin.SCHEMA_TREE_CONTEXT_MENU)
    val dialog = ExportToFileDialogViewImpl(project, params)
    val dialogListener = mock<ExportToFileDialogView.Listener>()
    dialog.addListener(dialogListener)

    createModalDialogAndInteractWithIt({ dialog.show() }) {
      verify(analyticsTracker).trackExportDialogOpened(params.actionOrigin)

      val treeWalker = TreeWalker(it.rootPane)

      paths.forEach { (dstPath, isValidPath) ->
        treeWalker.destinationPathTextField().text = dstPath
        assertWithMessage("Validating: $dstPath")
          .that(treeWalker.actionButton("Export").isEnabled)
          .isEqualTo(isValidPath)
      }

      treeWalker.actionButton("Cancel").doClick()
    }

    assertThat(dialog.exitCode).isEqualTo(CANCEL_EXIT_CODE)
    verifyNoMoreInteractions(dialogListener)
  }

  fun test_exportRequest_exportDatabase_db() {
    test_exportRequest_exportDatabase(DB)
  }

  fun test_exportRequest_exportDatabase_csv() {
    test_exportRequest_exportDatabase(CSV(VERTICAL_BAR))
  }

  fun test_exportRequest_exportDatabase_sql() {
    test_exportRequest_exportDatabase(SQL)
  }

  private fun test_exportRequest_exportDatabase(dstFormat: ExportFormat) {
    val dialogParams = ExportDatabaseDialogParams(databaseId, Origin.QUERY_RESULTS_EXPORT_BUTTON)
    val destinationPath = createDestinationPath(dialogParams, dstFormat)
    val expectedRequest = ExportDatabaseRequest(databaseId, dstFormat, destinationPath)

    test_exportRequest(dialogParams, expectedRequest)
  }

  fun test_exportRequest_exportTable_csv() {
    test_exportRequest_exportTable(CSV(TAB))
  }

  fun test_exportRequest_exportTable_sql() {
    test_exportRequest_exportTable(SQL)
  }

  private fun test_exportRequest_exportTable(dstFormat: ExportFormat) {
    val table = table1.name
    val dialogParams = ExportTableDialogParams(databaseId, table, Origin.UNKNOWN_ORIGIN)
    val destinationPath = createDestinationPath(dialogParams, dstFormat)
    val expectedRequest = ExportTableRequest(databaseId, table, dstFormat, destinationPath)

    test_exportRequest(dialogParams, expectedRequest)
  }

  fun test_exportRequest_exportQuery_csv() {
    val srcQuery = query
    val dialogParams =
      ExportQueryResultsDialogParams(databaseId, srcQuery, Origin.SCHEMA_TREE_CONTEXT_MENU)
    val dstFormat = CSV(COMMA)
    val destinationPath = createDestinationPath(dialogParams, dstFormat)
    val expectedRequest =
      ExportQueryResultsRequest(databaseId, srcQuery, dstFormat, destinationPath)

    test_exportRequest(dialogParams, expectedRequest)
  }

  private fun test_exportRequest(dialogParams: ExportDialogParams, expectedRequest: ExportRequest) {
    val dialog = ExportToFileDialogViewImpl(project, dialogParams)
    val dialogListener = mock<ExportToFileDialogView.Listener>()
    dialog.addListener(dialogListener)

    createModalDialogAndInteractWithIt({ dialog.show() }) {
      verify(analyticsTracker).trackExportDialogOpened(dialogParams.actionOrigin)
      val treeWalker = TreeWalker(it.rootPane)
      val format = expectedRequest.format
      treeWalker.formatButtons().single { it.text == format.displayName }.doClick()
      if (format is CSV)
        treeWalker.delimiterComboBox().run {
          selectedItem = items.single { it == format.delimiter.displayName }
        }
      treeWalker.destinationPathTextField().text = expectedRequest.dstPath.toFile().canonicalPath

      val latch = CountDownLatch(1)
      dialog.addListener(
        object : ExportToFileDialogView.Listener {
          override fun exportRequestSubmitted(params: ExportRequest) = latch.countDown()
        }
      )
      treeWalker.actionButton("Export").doClick()
      latch.await(5, TimeUnit.SECONDS)
    }

    assertThat(dialog.exitCode).isEqualTo(OK_EXIT_CODE)
    verify(dialogListener).exportRequestSubmitted(expectedRequest)
    verifyNoMoreInteractions(dialogListener)
  }

  private fun TreeWalker.formatButtons() = descendants().filterIsInstance<JRadioButton>()

  private fun TreeWalker.delimiterComboBox() =
    descendants().filterIsInstance<ComboBox<String>>().single()

  private fun TreeWalker.destinationPathTextField() =
    descendants().filterIsInstance<ExtendableTextField>().single()

  private fun TreeWalker.actionButton(text: String) =
    descendants().filterIsInstance<JButton>().single { it.text == text }

  private fun expectedFileExtension(format: ExportFormat, params: ExportDialogParams): String =
    when {
      format == DB && params is ExportDatabaseDialogParams -> "db"
      format is CSV && params is ExportDatabaseDialogParams -> "zip"
      format is CSV -> "csv"
      format == SQL -> "sql"
      else -> throw IllegalArgumentException()
    }

  private val ExportFormat.displayName
    get(): String =
      when (this) {
        DB -> "DB"
        SQL -> "SQL"
        is CSV -> "CSV"
      }

  private val Delimiter.displayName: String
    get() =
      when (this) {
        SEMICOLON -> "Semicolon (;)"
        TAB -> "Tab (↹)"
        COMMA -> "Comma (,)"
        VERTICAL_BAR -> "Vertical Bar (|)"
        SPACE -> "Space (␣)"
        else -> throw IllegalArgumentException()
      }

  private fun createDestinationPath(params: ExportDialogParams, format: ExportFormat) =
    Path.of(getUserHome(), "exported-file.${expectedFileExtension(format, params)}")
      .toFile()
      .canonicalFile
      .toPath()

  private val <T> ComboBox<T>.items
    get() = (0 until itemCount).map { getItemAt(it) }
}
