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
import com.android.tools.idea.backup.BackupManager.Companion.NOTIFICATION_GROUP
import com.android.tools.idea.backup.PostBackupDialog.Mode.EXISTING_CONFIG
import com.android.tools.idea.backup.PostBackupDialog.Mode.NEW_CONFIG
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.RunDialog
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import icons.StudioIcons
import java.awt.Component
import java.io.File
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants.LEADING
import kotlin.io.path.pathString

internal class PostBackupDialog(private val project: Project, private val backupPath: Path) :
  DialogWrapper(project) {
  private enum class Mode {
    EXISTING_CONFIG,
    NEW_CONFIG,
  }

  private var mode = EXISTING_CONFIG
  private var selectedSetting: RunnerAndConfigurationSettings? = null
  private var openRunConfigWhenDone = false
  private var setAsCurrentRunConfig = true

  init {
    init()
    title = "Add To Run Configuration"
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      buttonsGroup {
          row {
            radioButton("Add to existing run configuration", EXISTING_CONFIG)
            val settings = getRunConfigSettings()
            if (settings.isNotEmpty()) {
              selectedSetting = settings.first()
            }
            comboBox(settings, RunConfigSettingRenderer()).bindItem(::selectedSetting)
          }
          row { radioButton("Add to a new run configuration", NEW_CONFIG) }
          row { checkBox("Open run configuration when done").bindSelected(::openRunConfigWhenDone) }
          row { checkBox("Set as current run configuration").bindSelected(::setAsCurrentRunConfig) }
        }
        .bind({ mode }, { mode = it })
    }
  }

  override fun doOKAction() {
    applyFields()
    // TODO(aalbert): Handle errors
    val settings =
      when (mode) {
        EXISTING_CONFIG -> selectedSetting
        NEW_CONFIG -> addNewRunConfigSetting()
      }
    val config = settings?.configuration as? AndroidRunConfiguration ?: return
    config.RESTORE_ENABLED = true
    config.RESTORE_FILE = backupPath.relativeToProject()
    when (openRunConfigWhenDone) {
      true -> RunDialog.editConfiguration(project, settings, "Edit Configuration")
      false -> showNotification(settings)
    }
    if (setAsCurrentRunConfig) {
      RunManager.getInstance(project).selectedConfiguration = settings
    }
    super.doOKAction()
  }

  private fun showNotification(settings: RunnerAndConfigurationSettings) {
    val notification =
      Notification(NOTIFICATION_GROUP, "Updated run configuration '${settings.name}'", INFORMATION)
    notification.addAction(
      object : AnAction("Open") {
        override fun actionPerformed(e: AnActionEvent) {
          RunDialog.editConfiguration(project, settings, "Edit Configuration")
        }
      }
    )
    Notifications.Bus.notify(notification, project)
  }

  private fun addNewRunConfigSetting(): RunnerAndConfigurationSettings {
    val runManager = RunManager.getInstance(project)
    val settings =
      runManager.createConfiguration("Restore", AndroidRunConfigurationType::class.java)
    runManager.setUniqueNameIfNeeded(settings.configuration)
    val applicationId = BackupService.getMetadata(backupPath).applicationId
    val module = findModule(applicationId)
    if (module != null) {
      val config = settings.configuration as AndroidRunConfiguration
      config.setModule(module)
    }
    settings.storeInDotIdeaFolder()
    runManager.addConfiguration(settings)
    return settings
  }

  private fun Path.relativeToProject() =
    pathString.removePrefix(project.basePath ?: "").removePrefix(File.separator)

  private fun getRunConfigSettings(): List<RunnerAndConfigurationSettings> {
    val runManager = RunManager.getInstance(project)
    val applicationId = BackupService.getMetadata(backupPath).applicationId
    val selectedConfiguration = runManager.selectedConfiguration
    return buildList {
      if (selectedConfiguration?.isApplicable(applicationId) == true) {
        add(selectedConfiguration)
      }
      runManager.allSettings
        .filter { it.isApplicable(applicationId) }
        .sortedBy { it.name }
        .forEach {
          if (it != selectedConfiguration) {
            add(it)
          }
        }
    }
  }

  private fun RunnerAndConfigurationSettings.isApplicable(applicationId: String): Boolean {
    val configuration = configuration as? AndroidRunConfiguration ?: return false
    return configuration.applicationIdProvider?.packageName == applicationId
  }

  private class RunConfigSettingRenderer : ListCellRenderer<RunnerAndConfigurationSettings?> {
    private val component = JLabel(StudioIcons.Shell.Filetree.ANDROID_PROJECT, LEADING)

    override fun getListCellRendererComponent(
      list: JList<out RunnerAndConfigurationSettings?>,
      value: RunnerAndConfigurationSettings?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): Component {
      component.text = value?.name ?: ""

      return component
    }
  }

  private fun findModule(applicationId: String) =
    project.getProjectSystem().findModulesWithApplicationId(applicationId).firstOrNull()
}
