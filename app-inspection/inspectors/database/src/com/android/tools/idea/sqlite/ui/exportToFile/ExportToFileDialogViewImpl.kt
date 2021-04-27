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
package com.android.tools.idea.sqlite.ui.exportToFile

import com.android.tools.idea.io.IdeFileUtils
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.model.Delimiter
import com.android.tools.idea.sqlite.model.Delimiter.*
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
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.google.common.base.Strings
import com.google.wireless.android.sdk.stats.AppInspectionEvent.DatabaseInspectorEvent.ExportDialogOpenedEvent
import com.intellij.CommonBundle
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.io.exists
import java.io.File
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JRadioButton

/**
 * @see ExportToFileDialogView
 */
class ExportToFileDialogViewImpl(
  val project: Project,
  val params: ExportDialogParams
) : DialogWrapper(project, true), ExportToFileDialogView {
  private val listeners = mutableListOf<ExportToFileDialogView.Listener>()
  private val analyticsTracker = DatabaseInspectorAnalyticsTracker.getInstance(project)

  private lateinit var formatButtonGroup: ButtonGroup
  private lateinit var delimiterLabel: JBLabel
  private lateinit var delimiterComboBox: ComboBox<String>
  private lateinit var saveLocationTextField: TextFieldWithBrowseButton

  init {
    val source = when (params) {
      is ExportDatabaseDialogParams -> "Database" // TODO(161081452): use DatabaseInspectorBundle for user facing strings
      is ExportTableDialogParams -> "Table"
      is ExportQueryResultsDialogParams -> "Query Results"
    }

    title = "Export $source"
    setOKButtonText("Export")
    setCancelButtonText("Cancel")
    super.init()
  }

  override fun show() {
    analyticsTracker.trackExportDialogOpened(params.actionOrigin)
    super.show()
  }

  override fun getHelpId(): String {
    return "org.jetbrains.android./r/studio-ui/db-inspector-help"
  }

  override fun addListener(listener: ExportToFileDialogView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ExportToFileDialogView.Listener) {
    listeners.remove(listener)
  }

  override fun getStyle(): DialogStyle = DialogStyle.COMPACT

  override fun createCenterPanel(): JComponent {
    // set up format buttons
    val formatLabel = JBLabel("File type:")
    formatButtonGroup = ButtonGroup()
    val formatDbRadioButton = createFormatButton(DB)
    val formatSqlRadioButton = createFormatButton(SQL)
    val formatCsvRadioButton = createFormatButton(CSV(SEMICOLON)) // CSV delimiter choice not affecting the outcome
    listOf(formatDbRadioButton, formatSqlRadioButton, formatCsvRadioButton).forEach { button ->
      formatButtonGroup.add(button)
      button.addActionListener {
        updateDelimiterEnabled()
        updateDestinationExtension()
      }
    }
    formatButtonGroup.elements.asSequence().first { it.isVisible }.isSelected = true

    // set up delimiter combo box
    delimiterComboBox = ComboBox(Delimiter.values().map { it.displayName }.toTypedArray())
    delimiterLabel = JBLabel(DatabaseInspectorBundle.message("export.dialog.delimiter.label"))
    delimiterLabel.labelFor = delimiterComboBox
    updateDelimiterEnabled()

    // set up destination path selection
    saveLocationTextField = TextFieldWithBrowseButton()
    val saveLocationLabel = JBLabel(DatabaseInspectorBundle.message("export.dialog.output.location.label"))
    saveLocationLabel.labelFor = saveLocationTextField
    createSuggestedPath()?.let { saveLocationTextField.text = it.toString() }
    saveLocationTextField.addActionListener { showSaveFileDialog() }

    // position items
    return ExportToFileDialogLayout.createLayout(
      formatLabel,
      formatDbRadioButton,
      formatSqlRadioButton,
      formatCsvRadioButton,
      delimiterLabel,
      delimiterComboBox,
      saveLocationLabel,
      saveLocationTextField
    )
  }

  // TODO(161081452): consider moving path logic to [ExportToFileController]
  private fun showSaveFileDialog() {
    val dialog: FileSaverDialog = FileChooserFactory.getInstance().createSaveFileDialog(
      FileSaverDescriptor("Save as...", "", selectedFormatExtension()), contentPanel
    )
    val pathSuggestion = saveLocationTextField.text.let { current ->
      if (current.isBlank()) null else LocalFileSystem.getInstance().findFileByPath(current)
    }
    val file = dialog.save(pathSuggestion?.parent, pathSuggestion?.name)
    file?.let { saveLocationTextField.text = it.file.absolutePath }
    this@ExportToFileDialogViewImpl.toFront()
  }

  private fun createFormatButton(format: ExportFormat): JRadioButton {
    val isSupported = when {
      params.srcDatabase.isInMemoryDatabase() -> format is CSV
      format is DB -> params is ExportDatabaseDialogParams
      format is SQL -> params !is ExportQueryResultsDialogParams
      format is CSV -> true
      else -> false
    }
    return JRadioButton(format.displayName).apply {
      isVisible = isSupported
      isEnabled = isSupported
    }
  }

  private fun updateDelimiterEnabled() {
    val enabled = selectedFormat() is CSV
    delimiterLabel.isEnabled = enabled
    delimiterComboBox.isEnabled = enabled
  }

  private fun createSuggestedPath(): Path? {
    val baseDir = IdeFileUtils.getDesktopDirectory() ?: VfsUtil.getUserHomeDir()?.toNioPath() ?: return null
    val databaseName = params.srcDatabase.name
    val fileName = FileUtil.sanitizeFileName(
      when (params) {
        is ExportDatabaseDialogParams -> databaseName
        is ExportTableDialogParams -> "$databaseName-${params.srcTable}"
        is ExportQueryResultsDialogParams -> "$databaseName-query-results"
      }, false)
    val extension = selectedFormatExtension()
    val extensionPart = if (extension.isBlank()) "" else ".$extension"
    return baseDir.resolve("$fileName$extensionPart")
  }

  private fun updateDestinationExtension() {
    val currentPath = File(saveLocationTextField.text)
    val parentDir = currentPath.parentFile
    val newPath = parentDir.resolve("${currentPath.nameWithoutExtension}.${selectedFormatExtension()}")
    saveLocationTextField.text = newPath.absolutePath
  }

  /** Returns a file extension appropriate for the selected format */
  private fun selectedFormatExtension(): String {
    val selectedFormat = selectedFormat()
    if (params is ExportDatabaseDialogParams && selectedFormat is CSV) return "zip"
    return selectedFormat.fileExtension
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private fun selectedFormat(): ExportFormat {
    val buttonText = formatButtonGroup.elements.asSequence().firstOrNull { it.isSelected }?.text
    return when (buttonText) {
      DB.displayName -> DB
      SQL.displayName -> SQL
      CSV(SEMICOLON).displayName -> CSV(delimiterFromDisplayName(delimiterComboBox.item)) // CSV delimiter choice not affecting the outcome
      else -> throw IllegalStateException("Expected an export format to be selected.")
    }
  }

  /** Combines selected options into an [ExportRequest] formed of all information needed for an export operation */
  private fun createExportRequest(): ExportRequest? {
    // gather params
    val dstPath: Path = File(saveLocationTextField.text).toPath()
    val format = selectedFormat()

    // validate params
    if (Strings.isNullOrEmpty(dstPath.toString()) || !showConfirmOverwriteDialog(project, dstPath)) return null

    // return as ExportInstructions
    return when (params) {
      is ExportDatabaseDialogParams -> ExportDatabaseRequest(params.srcDatabase, format, dstPath)
      is ExportTableDialogParams -> ExportTableRequest(params.srcDatabase, params.srcTable, format, dstPath)
      is ExportQueryResultsDialogParams -> ExportQueryResultsRequest(params.srcDatabase, params.query, format, dstPath)
    }
  }

  /** Checks if selected destination already exists, and if so asks the user whether they are OK with overwriting the existing file */
  private fun showConfirmOverwriteDialog(project: Project, file: Path): Boolean {
    // TODO(161081452): consider moving path logic to [ExportToFileController]
    if (!file.exists()) return true
    val result = Messages.showYesNoDialog(
      project,
      DatabaseInspectorBundle.message(
        "export.dialog.file.already.exists.overwrite.prompt",
        file.fileName.toString(), file.parent.toString()),
      DatabaseInspectorBundle.message("export.dialog.file.already.exists.overwrite.title"),
      CommonBundle.message("button.overwrite"),
      CommonBundle.message("button.cancel"),
      Messages.getWarningIcon())
    return (result == Messages.YES)
  }

  override fun doOKAction() {
    createExportRequest()?.let { params ->
      listeners.forEach { it.exportRequestSubmitted(params) }
      super.doOKAction()
    }
  }

  private val Delimiter.displayName
    get() : String = DatabaseInspectorBundle.message(
      when (this) {
        SEMICOLON -> "export.dialog.delimiter.semicolon.label"
        TAB -> "export.dialog.delimiter.tab.label"
        COMMA -> "export.dialog.delimiter.comma.label"
        VERTICAL_BAR -> "export.dialog.delimiter.vertical_bar.label"
        SPACE -> "export.dialog.delimiter.space.label"
      }
    )

  private fun delimiterFromDisplayName(displayName: String): Delimiter = Delimiter.values().first { it.displayName == displayName }

  private val ExportFormat.fileExtension
    get() : String = when (this) {
      DB -> "db"
      SQL -> "sql"
      is CSV -> "csv"
    }

  private val ExportFormat.displayName
    get() : String = when (this) {
      DB -> "DB"
      SQL -> "SQL"
      is CSV -> "CSV"
    }
}
