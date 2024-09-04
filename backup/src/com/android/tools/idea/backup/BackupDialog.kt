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
import com.android.tools.idea.backup.BackupFileType.FILE_SAVER_DESCRIPTOR
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.TextFieldWithStoredHistory
import com.intellij.ui.scale.JBUIScale
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.GroupLayout
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrSelf

private const val TYPE_FIELD_WIDTH = 100
private const val PATH_FIELD_WIDTH = 500

internal class BackupDialog(
  private val project: Project,
  private val fileNameHint: String,
  private val initialDirectory: Path,
) : DialogWrapper(project) {
  private val typeComboBox = ComboBox(DefaultComboBoxModel(BackupType.entries.toTypedArray()))

  var backupPath: Path = initialDirectory.resolve("$fileNameHint.${BackupFileType.defaultExtension}")
    private set
  val type: BackupType get() = typeComboBox.item

  init {
    init()
    title = "Backup App State"
    typeComboBox.renderer = ListCellRenderer { _, value, _, _, _ -> JLabel(value.displayName) }
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(null)
    val layout = GroupLayout(panel)
    layout.autoCreateContainerGaps = true
    layout.autoCreateGaps = true

    val typeLabel = JLabel("Backup type:")
    val fileLabel = JLabel("Backup file:")

    val fileTextField = TextFieldWithStoredHistoryWithBrowseButton()
    fileTextField.addActionListener {
      val path =
        FileChooserFactory.getInstance()
          .createSaveFileDialog(FILE_SAVER_DESCRIPTOR, project)
          .save(backupPath.parent, backupPath.nameWithoutExtension)
          ?.file
          ?.toPath()
      if (path != null) {
        backupPath = path
        fileTextField.setTextAndAddToHistory(path.relative().pathString)
      }
    }

    fileTextField.text = initialDirectory.resolve("${fileNameHint}.${BackupFileType.defaultExtension}").relative().pathString

    layout.setHorizontalGroup(
      layout.createSequentialGroup()
        .addGroup(
          layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(typeLabel)
            .addComponent(fileLabel)
        )
        .addGroup(
          layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addComponent(typeComboBox, DEFAULT_SIZE, DEFAULT_SIZE, JBUIScale.scale(TYPE_FIELD_WIDTH))
            .addComponent(fileTextField, JBUIScale.scale(PATH_FIELD_WIDTH), DEFAULT_SIZE, DEFAULT_SIZE)
        )
    )
    layout.setVerticalGroup(
      layout.createSequentialGroup()
        .addGroup(
          layout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(typeLabel)
            .addComponent(typeComboBox)
        )
        .addGroup(
          layout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(fileLabel)
            .addComponent(fileTextField)
        )
    )

    panel.layout = layout
    return panel
  }

  private fun Path.relative() = relativeToOrSelf(Path.of(project.basePath!!))

  /**
   * Based on [com.intellij.ui.TextFieldWithHistoryWithBrowseButton]
   */
  private class TextFieldWithStoredHistoryWithBrowseButton
    : ComponentWithBrowseButton<TextFieldWithHistory>(TextFieldWithStoredHistory("Backup.File.History"), null) {
    var text: String
      get() = childComponent.text
      set(text) {
        childComponent.text = text
      }

    fun setTextAndAddToHistory(text: String) {
      childComponent.setTextAndAddToHistory(text)
    }
  }
}