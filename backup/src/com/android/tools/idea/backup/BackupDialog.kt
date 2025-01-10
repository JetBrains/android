/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.backup

import com.android.backup.BackupType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.UIBundle
import com.intellij.ui.scale.JBUIScale
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.GroupLayout
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.event.DocumentEvent
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString
import org.jetbrains.annotations.VisibleForTesting

private const val APPLICATION_ID_FIELD_WIDTH = 300
private const val TYPE_FIELD_WIDTH = 100
private const val PATH_FIELD_WIDTH = 500

private val DEFAULT_BACKUP_FILENAME = "application.${BackupFileType.defaultExtension}"

internal class BackupDialog(private val project: Project, initialApplicationId: String) :
  DialogWrapper(project) {
  private val applicationIds =
    project.getService(ProjectAppsProvider::class.java).getApplicationIds()
  private val applicationIdComboBox =
    ComboBox(DefaultComboBoxModel(applicationIds.sorted().toTypedArray())).apply {
      name = "applicationIdComboBox"
    }
  private val typeComboBox =
    ComboBox(DefaultComboBoxModel(BackupType.entries.toTypedArray())).apply {
      name = "typeComboBox"
    }
  private val fileTextField =
    BackupFileTextField.createFileSaver(project) { fileSetByChooser = true }
      .apply {
        textComponent.document.addDocumentListener(
          object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
              isOKActionEnabled = text.isNotBlank()
              try {
                Path.of(text)
              } catch (_: InvalidPathException) {
                isOKActionEnabled = false
              }
            }
          }
        )
        name = "fileTextField"
      }
  private var fileSetByChooser = false
  private val properties
    get() = PropertiesComponent.getInstance(project)

  val applicationId: String
    get() = applicationIdComboBox.item

  val backupPath: Path
    get() {
      val path = Path.of(fileTextField.text).absoluteInProject(project)
      return when (path.extension) {
        BackupFileType.defaultExtension -> path
        else -> Path.of("${path.pathString}.${BackupFileType.defaultExtension}")
      }
    }

  val type: BackupType
    get() = typeComboBox.item

  init {
    init()
    title = "Backup App State"
    if (applicationIds.contains(initialApplicationId)) {
      applicationIdComboBox.item = initialApplicationId
    }
    typeComboBox.item = getLastUsedType()
    typeComboBox.renderer = ListCellRenderer { _, value, _, _, _ -> JLabel(value.displayName) }
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(null)
    val layout = GroupLayout(panel)
    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    val applicationIdLabel = JLabel("Application ID:")
    val typeLabel = JLabel("Backup type:")
    val fileLabel = JLabel("Backup file:")

    fileTextField.text = getLastUsedFile()

    layout.setHorizontalGroup(
      layout
        .createSequentialGroup()
        .addGroup(
          layout
            .createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(applicationIdLabel)
            .addComponent(typeLabel)
            .addComponent(fileLabel)
        )
        .addGroup(
          layout
            .createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(
              applicationIdComboBox,
              DEFAULT_SIZE,
              DEFAULT_SIZE,
              JBUIScale.scale(APPLICATION_ID_FIELD_WIDTH),
            )
            .addComponent(
              typeComboBox,
              DEFAULT_SIZE,
              DEFAULT_SIZE,
              JBUIScale.scale(TYPE_FIELD_WIDTH),
            )
            .addComponent(
              fileTextField,
              JBUIScale.scale(PATH_FIELD_WIDTH),
              DEFAULT_SIZE,
              DEFAULT_SIZE,
            )
        )
    )
    layout.setVerticalGroup(
      layout
        .createSequentialGroup()
        .addGroup(
          layout
            .createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(applicationIdLabel)
            .addComponent(applicationIdComboBox)
        )
        .addGroup(
          layout
            .createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(typeLabel)
            .addComponent(typeComboBox)
        )
        .addGroup(
          layout
            .createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(fileLabel)
            .addComponent(fileTextField)
        )
    )

    panel.layout = layout
    return panel
  }

  override fun doOKAction() {
    setLastUsedFile(backupPath.relativeToProject(project).pathString)
    setLastUsedType(typeComboBox.item)
    fileTextField.addCurrentTextToHistory()
    if (backupPath.exists() && !fileSetByChooser) {
      @Suppress("DialogTitleCapitalization")
      val result =
        Messages.showYesNoDialog(
          UIBundle.message("file.chooser.save.dialog.confirmation", backupPath.fileName),
          UIBundle.message("file.chooser.save.dialog.confirmation.title"),
          Messages.getWarningIcon(),
        )
      if (result != Messages.YES) {
        return
      }
    }
    super.doOKAction()
  }

  private fun getLastUsedFile(): String {
    return properties.getValue(LAST_USED_FILE_KEY) ?: DEFAULT_BACKUP_FILENAME
  }

  private fun setLastUsedFile(path: String) {
    properties.setValue(LAST_USED_FILE_KEY, path)
  }

  private fun getLastUsedType(): BackupType {
    return when (val name = properties.getValue(LAST_USED_TYPE_KEY)) {
      null -> BackupType.DEVICE_TO_DEVICE
      else -> BackupType.valueOf(name)
    }
  }

  private fun setLastUsedType(type: BackupType) {
    properties.setValue(LAST_USED_TYPE_KEY, type.name)
  }

  companion object {
    @VisibleForTesting internal const val LAST_USED_FILE_KEY = "Backup.Last.Used.File"

    @VisibleForTesting internal const val LAST_USED_TYPE_KEY = "Backup.Last.Used.Type"
  }
}
