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
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.RunConfigSection
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.util.absoluteInProject
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
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
  private val freshInstall = propertyGraph.property(false)
  private var backupFile = propertyGraph.property("")
  private var restoreSupported = propertyGraph.property(true)

  override fun getComponent(parentDisposable: Disposable): Component {
    return panel {
      group(message("restore.run.config.group")) {
          row { checkBox(message("restore.run.config.checkbox")).bindSelected(restoreApp) }
          indent {
              row { backupFileChooser(PATH_FIELD_WIDTH).bindText(backupFile) }
              row {
                checkBox(message("restore.run.config.fresh.install.checkbox"))
                  .bindSelected(freshInstall)
              }
            }
            .enabledIf(restoreApp)
        }
        .enabledIf(restoreSupported)
    }
  }

  override fun resetFrom(runConfiguration: RunConfiguration) {
    val config = runConfiguration as? AndroidRunConfiguration ?: return
    restoreSupported.set(config.DEPLOY_AS_INSTANT)
    restoreApp.set(config.RESTORE_ENABLED)
    backupFile.set(config.RESTORE_FILE)
    freshInstall.set(config.RESTORE_FRESH_INSTALL_ONLY)
    restoreSupported.set(!config.DEPLOY_AS_INSTANT)
  }

  override fun applyTo(runConfiguration: RunConfiguration) {
    val config = runConfiguration as? AndroidRunConfiguration ?: return
    config.RESTORE_ENABLED = restoreApp.get()
    config.RESTORE_FILE = backupFile.get()
    config.RESTORE_FRESH_INSTALL_ONLY = freshInstall.get()
  }

  override fun validate(runConfiguration: RunConfiguration): List<ValidationError> {
    val config = runConfiguration as? AndroidRunConfiguration ?: return emptyList()
    if (!config.RESTORE_ENABLED || config.DEPLOY_AS_INSTANT) {
      return emptyList()
    }
    val file = config.RESTORE_FILE
    if (file.isBlank()) {
      return listOf(ValidationError.warning(message("backup.file.missing")))
    }

    val path = Path.of(file).absoluteInProject(project)
    if (path.notExists()) {
      return listOf(ValidationError.warning(message("backup.file.not.exist")))
    }

    try {
      val fileApplicationId = BackupService.validateBackupFile(path).applicationId
      val packageName = projectSystem.getApplicationIdProvider(runConfiguration)?.packageName
      if (packageName != null && fileApplicationId != packageName) {
        return listOf(ValidationError.warning(message("backup.file.mismatch", fileApplicationId)))
      }
    } catch (_: Exception) {
      return listOf(ValidationError.warning(message("backup.file.invalid")))
    }

    return emptyList()
  }

  override fun updateBasedOnInstantState(instantAppDeploy: Boolean) {
    restoreSupported.set(!instantAppDeploy)
  }

  private fun Row.backupFileChooser(width: Int): Cell<BackupFileTextField> {
    return cell(BackupFileTextField.createFileChooser(project)).applyToComponent {
      // TODO(aalbert): Figure out how to resize this properly. `Cell.resizeableColumn` doesn't work
      preferredSize = Dimension(JBUI.scale(width), preferredSize.height)
    }
  }
}

fun Cell<BackupFileTextField>.bindText(
  property: ObservableMutableProperty<String>
): Cell<BackupFileTextField> {
  return applyToComponent { textComponent.bind(property) }
}
