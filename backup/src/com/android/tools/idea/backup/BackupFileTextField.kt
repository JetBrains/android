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
import com.intellij.ui.TextFieldWithStoredHistory
import java.nio.file.Path
import javax.swing.text.JTextComponent
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrSelf

private const val FILE_HISTORY_PROPERTY = "Backup.File.History"

/**
 * A TextField for selecting a backup file
 *
 * Includes a browse button and a persisted history dropdown.
 *
 *  Based on [com.intellij.ui.TextFieldWithHistoryWithBrowseButton]
 */
class BackupFileTextField : ComponentWithBrowseButton<TextFieldWithHistory>(
  TextFieldWithStoredHistory(FILE_HISTORY_PROPERTY),
  null,
), TextAccessor {

  val textComponent: JTextComponent = childComponent.textEditor

  fun setTextAndAddToHistory(text: String) {
    childComponent.setTextAndAddToHistory(text)
  }

  override fun setText(text: String) {
    childComponent.text = text
  }

  override fun getText(): String = childComponent.text

  companion object {
    /**
     * Create a BackupFileTextField for saving a backup file
     */
    fun createFileSaver(project: Project, onFileChosen: (Path) -> Unit): BackupFileTextField {
      val textField = BackupFileTextField()
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
          textField.setTextAndAddToHistory(path.relativeToOrSelf(Path.of(project.basePath!!)).pathString)
          onFileChosen(path)
        }
      }
      return textField
    }

    /**
     * Create a BackupFileTextField for choosing a backup file
     */
    fun createFileChooser(project: Project): BackupFileTextField {
      val textField = BackupFileTextField()
      textField.addActionListener {
        val path =
          FileChooserFactory.getInstance()
            .createFileChooser(FILE_CHOOSER_DESCRIPTOR, project, null)
            .choose(project)
            .firstOrNull()
            ?.toNioPath()
        if (path != null) {
          textField.setTextAndAddToHistory(path.relativeToOrSelf(Path.of(project.basePath!!)).pathString)
        }
      }
      return textField
    }
  }
}
