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

import com.android.backup.BackupService
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupFileType.FILE_CHOOSER_DESCRIPTOR
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.RunConfigSection
import com.android.tools.idea.run.ValidationError
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.nio.file.Path
import kotlin.io.path.notExists

private const val PATH_FIELD_WIDTH = 500

/** UI for the Restore App Run Configuration section */
class RestoreRunConfigSection(private val project: Project) : RunConfigSection {
  private val projectSystem = project.getProjectSystem()
  private val propertyGraph = PropertyGraph()
  private val restoreApp = propertyGraph.property(false)
  private var backupFile = propertyGraph.property("")

  override fun getComponent(parentDisposable: Disposable): Component {
    return panel {
      group(message("restore.run.config.group")) {
        row { checkBox(message("restore.run.config.checkbox")).bindSelected(restoreApp) }
        indent {
          row { backupFileChooser(PATH_FIELD_WIDTH).enabledIf(restoreApp).bindText(backupFile) }
        }
      }
    }
  }

  override fun resetFrom(runConfiguration: RunConfiguration) {
    val config = runConfiguration as? AndroidRunConfiguration ?: return
    restoreApp.set(config.RESTORE_ENABLED)
    backupFile.set(config.RESTORE_FILE)
  }

  override fun applyTo(runConfiguration: RunConfiguration) {
    val config = runConfiguration as? AndroidRunConfiguration ?: return
    config.RESTORE_ENABLED = restoreApp.get()
    config.RESTORE_FILE = backupFile.get()
  }

  override fun validate(runConfiguration: RunConfiguration): List<ValidationError> {
    val config = runConfiguration as? AndroidRunConfiguration ?: return emptyList()
    if (!config.RESTORE_ENABLED) {
      return emptyList()
    }
    val file = config.RESTORE_FILE
    if (file.isBlank()) {
      return listOf(ValidationError.warning(message("backup.file.missing")))
    }

    val path =
      when (file.startsWith('/')) {
        true -> Path.of(file)
        false -> Path.of(project.basePath ?: "", file)
      }
    if (path.notExists()) {
      return listOf(ValidationError.warning(message("backup.file.not.exist")))
    }

    try {
      val fileApplicationId = BackupService.validateBackupFile(path)
      val packageName = projectSystem.getApplicationIdProvider(runConfiguration)?.packageName
      if (packageName != null && fileApplicationId != packageName) {
        return listOf(ValidationError.warning(message("backup.file.mismatch", fileApplicationId)))
      }
    } catch (e: Exception) {
      return listOf(ValidationError.warning(message("backup.file.invalid")))
    }

    return emptyList()
  }

  private fun Row.backupFileChooser(width: Int): Cell<TextFieldWithBrowseButton> {
    val cell =
      textFieldWithBrowseButton(
        message("backup.choose.restore.file.dialog.title"),
        project,
        FILE_CHOOSER_DESCRIPTOR,
      ) {
        val file = it.path
        val basePath = project.basePath
        file.takeIf { basePath == null } ?: file.removePrefix("$basePath/")
      }
    return cell.apply {
      // TODO(aalbert): Figure out how to resize this properly. `Cell.resizeableColumn` doesn't work
      applyToComponent { preferredSize = Dimension(JBUI.scale(width), preferredSize.height) }
    }
  }
}
