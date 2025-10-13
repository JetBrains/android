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

import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.adtui.swing.getDescendant
import com.android.tools.analytics.UsageTrackerRule
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
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogViewImpl
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin.SCHEMA_TREE_CONTEXT_MENU
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent.Origin.UNKNOWN_ORIGIN
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.Type.EXPORT_DIALOG_OPENED
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.SystemProperties.getUserHome
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JRadioButton
import kotlin.io.path.pathString
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test suite verifying Export-to-File Dialog behaviour */
@RunsInEdt
class ExportToFileDialogTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val usageTrackerRule = UsageTrackerRule()
  private val project
    get() = projectRule.project

  private val disposable
    get() = disposableRule.disposable

  @get:Rule val rule = RuleChain(projectRule, disposableRule, usageTrackerRule, EdtRule())

  private val inFileDatabaseId =
    SqliteDatabaseId.fromFileDatabase(DatabaseFileData(MockVirtualFile("name")))
  private val inMemoryDatabaseId = SqliteDatabaseId.fromLiveDatabase(":memory: database1337", 1337)

  // memory-backed
  private val column1 =
    SqliteColumn("c1", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
  private val column2 =
    SqliteColumn("c2", SqliteAffinity.TEXT, isNullable = false, inPrimaryKey = false)
  private val table1 = SqliteTable("t1", listOf(column1, column2), null, false)
  private val query =
    SqliteStatement(
      SELECT,
      "select * from ${table1.name} where ${table1.columns.first().name} == qwerty",
    )

  @Before
  fun setUp() {
    enableHeadlessDialogs(disposable)
  }

  @Test
  fun showDialog_tracksUsage() {
    val params = exportDatabaseDialogParams(actioOrigin = SCHEMA_TREE_CONTEXT_MENU)
    val dialog = exportToFileDialogViewImpl(params)

    createModalDialogAndInteractWithIt({ dialog.show() }) {}

    assertThat(usageTrackerRule.events())
      .contains(exportDialogOpenedEvent(SCHEMA_TREE_CONTEXT_MENU))
  }

  @Test
  fun exportDatabase_title() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams())

    createModalDialogAndInteractWithIt({ dialog.show() }) {
      assertThat(it.title).isEqualTo("Export Database")
    }
  }

  @Test
  fun exportDatabase_fileDatabaseFormats() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      assertThat(dialog.formatButtons().map { it.text }).containsExactly("CSV", "SQL", "DB")
    }
  }

  @Test
  fun exportDatabase_memoryDatabaseFormats() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inMemoryDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      assertThat(dialog.formatButtons().map { it.text }).containsExactly("CSV")
    }
  }

  @Test
  fun exportDatabase_fileExtension_db() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("DB")

      assertThat(dialog.destinationPathTextField().text).endsWith(".db")
    }
  }

  @Test
  fun exportDatabase_fileExtension_sql() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")

      assertThat(dialog.destinationPathTextField().text).endsWith(".sql")
    }
  }

  @Test
  fun exportDatabase_fileExtension_csv() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")

      assertThat(dialog.destinationPathTextField().text).endsWith(".zip")
    }
  }

  @Test
  fun exportTable_title() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams())

    createModalDialogAndInteractWithIt({ dialog.show() }) {
      assertThat(it.title).isEqualTo("Export Table")
    }
  }

  @Test
  fun exportTable_fileDatabaseFormats() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      assertThat(dialog.formatButtons().map { it.text }).containsExactly("CSV", "SQL")
    }
  }

  @Test
  fun exportTable_memoryDatabaseFormats() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inMemoryDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      assertThat(dialog.formatButtons().map { it.text }).containsExactly("CSV")
    }
  }

  @Test
  fun exportTable_fileExtension_sql() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")

      assertThat(dialog.destinationPathTextField().text).endsWith(".sql")
    }
  }

  @Test
  fun exportTable_fileExtension_csv() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")

      assertThat(dialog.destinationPathTextField().text).endsWith(".csv")
    }
  }

  @Test
  fun exportQuery_title() {
    val dialog = exportToFileDialogViewImpl(exportQueryDialogParams())

    createModalDialogAndInteractWithIt({ dialog.show() }) {
      assertThat(it.title).isEqualTo("Export Query Results")
    }
  }

  @Test
  fun exportQuery_fileDatabaseFormats() {
    val dialog = exportToFileDialogViewImpl(exportQueryDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      assertThat(dialog.formatButtons().map { it.text }).containsExactly("CSV")
    }
  }

  @Test
  fun exportQuery_memoryDatabaseFormats() {
    val dialog = exportToFileDialogViewImpl(exportQueryDialogParams(inMemoryDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      assertThat(dialog.formatButtons().map { it.text }).containsExactly("CSV")
    }
  }

  @Test
  fun exportQuery_fileExtension_csv() {
    val dialog = exportToFileDialogViewImpl(exportQueryDialogParams(inFileDatabaseId))
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")

      assertThat(dialog.destinationPathTextField().text).endsWith(".csv")
    }
  }

  @Test
  fun delimiterView_visibleForCsv() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")

      assertThat(dialog.delimiterComboBox().isVisible).isTrue()
    }
  }

  @Test
  fun delimiterView_invisibleForSql() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")

      assertThat(dialog.delimiterComboBox().isVisible).isTrue()
    }
  }

  @Test
  fun delimiterView_invisibleForDb() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("DB")

      assertThat(dialog.delimiterComboBox().isVisible).isTrue()
    }
  }

  @Test
  fun delimiterView_items() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")

      assertThat(dialog.delimiterComboBox().items)
        .containsExactly("Semicolon (;)", "Tab (↹)", "Comma (,)", "Vertical Bar (|)", "Space (␣)")
    }
  }

  @Test
  fun clickCancel() {
    val dialog = exportToFileDialogViewImpl()
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog -> dialog.clickCancel() }

    assertThat(dialog.exitCode).isEqualTo(CANCEL_EXIT_CODE)
    assertThat(requestListener.requests).isEmpty()
  }

  @Test
  fun dstPathValidation_validPath() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = "/out.zip"

      assertThat(dialog.exportButton().isEnabled).isTrue()
    }
  }

  @Test
  fun dstPathValidation_validPathLonger() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = Paths.get(getUserHome(), "out.zip").toString()

      assertThat(dialog.exportButton().isEnabled).isTrue()
    }
  }

  @Test
  fun dstPathValidation_validPathTilde() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = "~/out.zip"

      assertThat(dialog.exportButton().isEnabled).isTrue()
    }
  }

  @Test
  fun dstPathValidation_emptyPath() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = ""

      assertThat(dialog.exportButton().isEnabled).isFalse()
    }
  }

  @Test
  fun dstPathValidation_blankPath() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = "   "

      assertThat(dialog.exportButton().isEnabled).isFalse()
    }
  }

  @Test
  fun dstPathValidation_noParent() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = "out.zip"

      assertThat(dialog.exportButton().isEnabled).isFalse()
    }
  }

  @Test
  fun dstPathValidation_targetIsExistingDir() {
    val dialog = exportToFileDialogViewImpl()
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.destinationPathTextField().text = getUserHome()

      assertThat(dialog.exportButton().isEnabled).isFalse()
    }
  }

  @Test
  fun dstPathValidation_multiplePaths() {
    listOf(
        "" to false,
        " " to false,
        "/" to false,
        "/out" to true,
        "/out/" to false,
        "/does-not-exist-path" to true,
        "/does-not-exist-path/" to false,
        "/does-not-exist-path/out" to false,
        Paths.get(getUserHome(), "out.zip").pathString to true,
        "" to false,
      )
      .forEach { (path, valid: Boolean) ->
        val dialog = exportToFileDialogViewImpl()
        createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
          dialog.destinationPathTextField().text = path

          assertThat(dialog.exportButton().isEnabled).isEqualTo(valid)
        }
      }
  }

  @Test
  fun exportDatabase_db() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("DB")
      dialog.destinationPathTextField().text = "/foo.db"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportDatabaseRequest(inFileDatabaseId, DB, Path.of("/foo.db")))
  }

  @Test
  fun exportDatabase_sql() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")
      dialog.destinationPathTextField().text = "/foo.sql"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportDatabaseRequest(inFileDatabaseId, SQL, Path.of("/foo.sql")))
  }

  @Test
  fun exportDatabase_csv_defaultDelimiter() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.destinationPathTextField().text = "/foo.zip"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportDatabaseRequest(inFileDatabaseId, CSV(SEMICOLON), Path.of("/foo.zip")))
  }

  @Test
  fun exportDatabase_csv() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.delimiterComboBox().selectedItem = VERTICAL_BAR.displayName
      dialog.destinationPathTextField().text = "/foo.zip"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(
        ExportDatabaseRequest(inFileDatabaseId, CSV(VERTICAL_BAR), Path.of("/foo.zip"))
      )
  }

  @Test
  fun exportDatabase_db_missingExtension() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("DB")
      dialog.destinationPathTextField().text = "/foo"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportDatabaseRequest(inFileDatabaseId, DB, Path.of("/foo.db")))
  }

  @Test
  fun exportDatabase_sql_missingExtension() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")
      dialog.destinationPathTextField().text = "/foo"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportDatabaseRequest(inFileDatabaseId, SQL, Path.of("/foo.sql")))
  }

  @Test
  fun exportDatabase_csv__missingExtension() {
    val dialog = exportToFileDialogViewImpl(exportDatabaseDialogParams(inFileDatabaseId))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.destinationPathTextField().text = "/foo"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportDatabaseRequest(inFileDatabaseId, CSV(SEMICOLON), Path.of("/foo.zip")))
  }

  @Test
  fun exportTable_sql() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId, table1.name))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")
      dialog.destinationPathTextField().text = "/foo.sql"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportTableRequest(inFileDatabaseId, "t1", SQL, Path.of("/foo.sql")))
  }

  @Test
  fun exportTable_csv() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId, table1.name))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.destinationPathTextField().text = "/foo.csv"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(
        ExportTableRequest(inFileDatabaseId, "t1", CSV(SEMICOLON), Path.of("/foo.csv"))
      )
  }

  @Test
  fun exportTable_sql_missingExtension() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId, table1.name))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("SQL")
      dialog.destinationPathTextField().text = "/foo"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(ExportTableRequest(inFileDatabaseId, "t1", SQL, Path.of("/foo.sql")))
  }

  @Test
  fun exportTable_csv_missingExtension() {
    val dialog = exportToFileDialogViewImpl(exportTableDialogParams(inFileDatabaseId, table1.name))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.destinationPathTextField().text = "/foo"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(
        ExportTableRequest(inFileDatabaseId, "t1", CSV(SEMICOLON), Path.of("/foo.csv"))
      )
  }

  @Test
  fun exportQuery_csv() {
    val dialog = exportToFileDialogViewImpl(exportQueryDialogParams(inFileDatabaseId, query))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.destinationPathTextField().text = "/foo.csv"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(
        ExportQueryResultsRequest(inFileDatabaseId, query, CSV(SEMICOLON), Path.of("/foo.csv"))
      )
  }

  @Test
  fun exportQuery_csv_missingExtension() {
    val dialog = exportToFileDialogViewImpl(exportQueryDialogParams(inFileDatabaseId, query))
    val requestListener = RequestListener()
    dialog.addListener(requestListener)
    createModalDialogAndInteractWithIt({ dialog.show() }) { dialog ->
      dialog.selectFormat("CSV")
      dialog.destinationPathTextField().text = "/foo"
      dialog.clickExport()
    }

    assertThat(requestListener.requests)
      .containsExactly(
        ExportQueryResultsRequest(inFileDatabaseId, query, CSV(SEMICOLON), Path.of("/foo.csv"))
      )
  }

  private val Delimiter.displayName: String
    get() =
      when (this) {
        SEMICOLON -> "Semicolon (;)"
        TAB -> "Tab (↹)"
        COMMA -> "Comma (,)"
        VERTICAL_BAR -> "Vertical Bar (|)"
        SPACE -> "Space (␣)"
      }

  private val <T> ComboBox<T>.items
    get() = (0 until itemCount).map { getItemAt(it) }

  private fun exportDatabaseDialogParams(
    databaseId: SqliteDatabaseId = inFileDatabaseId,
    actioOrigin: Origin = UNKNOWN_ORIGIN,
  ) = ExportDatabaseDialogParams(databaseId, actioOrigin)

  private fun exportTableDialogParams(
    databaseId: SqliteDatabaseId = inFileDatabaseId,
    table: String = table1.name,
    actioOrigin: Origin = UNKNOWN_ORIGIN,
  ) = ExportTableDialogParams(databaseId, table, actioOrigin)

  private fun exportQueryDialogParams(
    databaseId: SqliteDatabaseId = inFileDatabaseId,
    query: SqliteStatement = this.query,
    actioOrigin: Origin = UNKNOWN_ORIGIN,
  ) = ExportQueryResultsDialogParams(databaseId, query, actioOrigin)

  private fun exportToFileDialogViewImpl(
    params: ExportDialogParams = exportDatabaseDialogParams()
  ) = ExportToFileDialogViewImpl(project, params)

  private class RequestListener : ExportToFileDialogView.Listener {
    val requests = mutableListOf<ExportRequest>()

    override fun exportRequestSubmitted(params: ExportRequest) {
      requests.add(params)
    }
  }
}

private fun UsageTrackerRule.events(): List<DatabaseInspectorEvent> =
  usages.mapNotNull { it.studioEvent.appInspectionEvent?.databaseInspectorEvent }

@Suppress("SameParameterValue")
private fun exportDialogOpenedEvent(origin: Origin) =
  DatabaseInspectorEvent.newBuilder()
    .setType(EXPORT_DIALOG_OPENED)
    .setExportDialogOpenedEvent(ExportDialogOpenedEvent.newBuilder().setOrigin(origin))
    .build()

private fun DialogWrapper.formatButtons() =
  rootPane.findAllDescendants<JRadioButton> { it.isVisible }.toList()

private fun DialogWrapper.delimiterComboBox() = rootPane.getDescendant<ComboBox<String>>()

private fun DialogWrapper.selectFormat(format: String) {
  formatButtons().first { it.text == format }.doClick()
}

private fun DialogWrapper.destinationPathTextField() = rootPane.getDescendant<ExtendableTextField>()

private fun DialogWrapper.exportButton() = rootPane.getDescendant<JButton> { it.text == "Export" }

private fun DialogWrapper.clickExport() =
  rootPane.getDescendant<JButton> { it.text == "Export" }.doClick()

private fun DialogWrapper.clickCancel() =
  rootPane.getDescendant<JButton> { it.text == "Cancel" }.doClick()
