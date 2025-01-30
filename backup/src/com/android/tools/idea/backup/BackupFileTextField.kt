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

import com.android.tools.idea.backup.BackupFileType.FILE_CHOOSER_DESCRIPTOR
import com.android.tools.idea.backup.BackupFileType.FILE_SAVER_DESCRIPTOR
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.ui.TextAccessor
import com.intellij.ui.TextFieldWithHistory
import java.nio.file.Path
import javax.swing.text.JTextComponent
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

/**
 * A TextField for selecting a backup file
 *
 * Includes a browse button and a persisted history dropdown.
 *
 * Based on [com.intellij.ui.TextFieldWithHistoryWithBrowseButton]
 */
class BackupFileTextField private constructor(project: Project) :
  ComponentWithBrowseButton<TextFieldWithHistory>(TextFieldWithProjectStoredHistory(project), null),
  TextAccessor {

  val textComponent: JTextComponent = childComponent.textEditor

  fun setTextAndAddToHistory(text: String) {
    childComponent.setTextAndAddToHistory(text)
  }

  fun addCurrentTextToHistory() {
    childComponent.addCurrentTextToHistory()
  }

  override fun setText(text: String) {
    childComponent.text = text
  }

  override fun getText(): String = childComponent.text

  /** Based on [com.intellij.ui.TextFieldWithStoredHistory] but with a `project` scope */
  private class TextFieldWithProjectStoredHistory(project: Project) : TextFieldWithHistory() {
    private val backupFileHistory = BackupFileHistory(project)

    init {
      reset()
    }

    override fun addCurrentTextToHistory() {
      super.addCurrentTextToHistory()
      backupFileHistory.setFileHistory(history)
    }

    fun reset() {
      val history = backupFileHistory.getFileHistory()
      setHistory(history)
      selectedItem = ""
    }
  }

  companion object {
    /** Create a BackupFileTextField for saving a backup file */
    fun createFileSaver(project: Project, onFileChosen: (Path) -> Unit): BackupFileTextField {
      val textField = BackupFileTextField(project)
      textField.addActionListener {
        val absolutePath = Path.of(textField.text).absoluteInProject(project)
        val parent = absolutePath.parent
        val nameWithoutExtension = absolutePath.nameWithoutExtension
        val path =
          FileChooserFactory.getInstance()
            .createSaveFileDialog(FILE_SAVER_DESCRIPTOR, project)
            .save(parent, nameWithoutExtension)
            ?.file
            ?.toPath()
        if (path != null) {
          textField.setTextAndAddToHistory(path.relativeToProject(project).pathString)
          onFileChosen(path)
        }
      }
      return textField
    }

    /** Create a BackupFileTextField for choosing a backup file */
    fun createFileChooser(project: Project): BackupFileTextField {
      val textField = BackupFileTextField(project)
      textField.addActionListener {
        val path =
          FileChooserFactory.getInstance()
            .createFileChooser(FILE_CHOOSER_DESCRIPTOR, project, null)
            .choose(project)
            .firstOrNull()
            ?.toNioPath()
        if (path != null) {
          textField.setTextAndAddToHistory(path.relativeToProject(project).pathString)
        }
      }
      return textField
    }
  }
}
