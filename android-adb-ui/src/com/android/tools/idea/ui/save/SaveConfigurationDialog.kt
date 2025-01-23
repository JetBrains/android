/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.save

import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.util.io.FileUtilRt.getExtension
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.time.Instant
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextField
import javax.swing.event.HyperlinkEvent

class SaveConfigurationDialog(
  project: Project,
  saveLocation: String,
  filenameTemplate: String,
  private val fileExtension: String,
  private val timestamp: Instant,
  private val sequentialNumber: Int,
) {

  val saveLocation: String
    get() = saveConfig.generalizeSaveLocation(saveLocationInternal.trim())
  val filenameTemplate: String
    get() = normalizeFilename(filenameTemplateInternal).replace(File.separatorChar, '/')
  private val saveConfig = project.service<SaveConfiguration>()
  private var saveLocationInternal: String = saveConfig.expandSaveLocation(saveLocation).replace('/', File.separatorChar)
  private var filenameTemplateInternal: String = filenameTemplate.replace('/', File.separatorChar)
  private lateinit var preview: JEditorPane
  private lateinit var saveLocationField: TextAccessor
  private lateinit var filenameTemplateField: JTextField
  private val validation = Validation()
  private val hyperlinkAction = HyperlinkEventAction { event ->
    if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      filenameTemplateField.simulateTyping(if (event.description == "%Nd") "%3d" else event.description)
    }
  }

  /** Creates contents of the dialog. */
  private fun createPanel(): DialogPanel {
    return panel {
      row(message("configure.screenshot.dialog.save.location")) {
        textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
          .also { it.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false) })
          .columns(COLUMNS_LARGE)
          .bindText(::saveLocationInternal)
          .validationOnInput(validation)
          .onChanged { preview.text = generatePreview() }
          .applyToComponent { saveLocationField = this }
      }
      row(message("configure.screenshot.dialog.filename")) {
        textField()
          .bindText(::filenameTemplateInternal)
          .columns(COLUMNS_MEDIUM)
          .validationOnInput(validation)
          .onChanged { preview.text = generatePreview() }
          .applyToComponent { filenameTemplateField = this }
      }
      row(message("configure.screenshot.dialog.preview")) {
        text(generatePreview()).applyToComponent { preview = this }
      }
      row("") {
        text(message("configure.screenshot.dialog.placeholders.description"), maxLineLength = 90)
      }
      row("") {
        text("${placeholder("%Y")} ${message("configure.screenshot.dialog.year.4.digits")}<br>" +
             "${placeholder("%y")} ${message("configure.screenshot.dialog.year.2.digits")}<br>" +
             "${placeholder("%M")} ${message("configure.screenshot.dialog.month")}<br>" +
             "${placeholder("%D")} ${message("configure.screenshot.dialog.day")}<br>" +
             "${placeholder("%H")} ${message("configure.screenshot.dialog.hour")}<br>" +
             "${placeholder("%m")} ${message("configure.screenshot.dialog.minute")}<br>" +
             "${placeholder("%S")} ${message("configure.screenshot.dialog.second")}<br>" +
             "${placeholder("%d")} ${message("configure.screenshot.dialog.sequential.number")}<br>" +
             "${placeholder("%Nd")} ${message("configure.screenshot.dialog.sequential.number.padded")}<br>" +
             "${placeholder("%p")} ${message("configure.screenshot.dialog.project.name")}<br>" +
             "${placeholder(File.separator)} ${message("configure.screenshot.dialog.directory.separator")}",
             action = hyperlinkAction)
      }
    }
  }

  private fun placeholder(text: String): String =
      "<code><a href='$text'><code>$text</a>${"&nbsp;".repeat(3 - text.length)}</code>"

  /** Creates the dialog wrapper. */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    return dialog(
        title = message("configure.screenshot.dialog.title"),
        resizable = true,
        panel = createPanel(),
        project = project,
        parent = parent)
  }

  private fun generatePreview(): String {
    return saveConfig.expandFilenamePattern(
        saveLocationField.text.trim(), normalizeFilename(filenameTemplateField.text), fileExtension, timestamp, sequentialNumber)
  }

  private fun normalizeFilename(filename: String): String {
    val trimmed = filename.trim().replace('/', File.separatorChar)
    return if (getExtension(trimmed).equals(fileExtension, ignoreCase = true)) getNameWithoutExtension(trimmed) else trimmed
  }

  private fun JTextField.simulateTyping(text: String) {
    val selectionStart = selectionStart
    this.text = this.text.replaceRange(selectionStart, selectionEnd, text)
    caretPosition = selectionStart + text.length
  }

  private inner class Validation : DialogValidation {

    override fun validate(): ValidationInfo? {
      val saveLocation = saveLocationField.text.trim().replace('/', File.separatorChar)
      checkPath(saveLocation)?.let {
          return ValidationInfo(message("configure.screenshot.dialog.error.invalid.directory", it), saveLocationField as JComponent)
      }

      val filenamePattern = normalizeFilename(filenameTemplateField.text)
      return when {
        filenamePattern.isEmpty() ->
            ValidationInfo(message("configure.screenshot.dialog.error.empty.filename"), filenameTemplateField)
        filenamePattern.startsWith(File.separator) ->
            ValidationInfo(message("configure.screenshot.dialog.error.leading.separator"), filenameTemplateField)
        filenamePattern.endsWith(File.separator) ->
            ValidationInfo(message("configure.screenshot.dialog.error.trailing.separator"), filenameTemplateField)
        filenamePattern.contains("..") || filenamePattern.contains(":") ->
            ValidationInfo(message("configure.screenshot.dialog.error.invalid.filename.generic"), filenameTemplateField)
        else -> checkPath(filenamePattern)?.let {
            ValidationInfo(message("configure.screenshot.dialog.error.invalid.filename", it), filenameTemplateField)
        }
      }
    }

    /** Checks is the given string is a valid file system path and returns an error message if not. */
    private fun checkPath(path: String): String? {
      try {
        Paths.get(path)
        return null
      }
      catch(e: InvalidPathException) {
        return e.reason
      }
    }
  }
}
