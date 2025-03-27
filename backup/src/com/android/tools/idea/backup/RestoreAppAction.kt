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

import com.android.tools.idea.actions.enableRichTooltip
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupManager.Source.RESTORE_APP_ACTION
import com.android.tools.idea.backup.RestoreAppAction.Config.Standalone
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Restores an Android Application from a backup file */
internal class RestoreAppAction(
  private val config: Config = Standalone,
  private val actionHelper: ActionHelper = ActionHelperImpl(),
  private val dialogFactory: DialogFactory = DialogFactoryImpl(),
) : AnAction() {
  override fun getActionUpdateThread() = BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    if (!StudioFlags.BACKUP_ENABLED.get()) {
      return
    }
    val project = e.project ?: return
    if (config == Standalone && RestoreAppActionGroup.showGroup(project)) {
      return
    }

    e.presentation.isVisible = true
    e.presentation.icon = config.presentation.icon
    e.presentation.text = config.presentation.text
    e.presentation.description = config.presentation.description

    if (
      (e.place == "MainToolbar" || e.place == "MainMenu") &&
        actionHelper.getDeployTargetCount(project) != 1
    ) {
      return e.presentation.enableRichTooltip(this, message("error.multiple.devices"))
    }

    e.presentation.isEnabled = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val coroutineScope = project.service<DeviceProvisionerService>().deviceProvisioner.scope
    coroutineScope.launch {
      val serialNumber = getDeviceSerialNumber(e)
      if (serialNumber == null) {
        project.showDialog(message("error.device.not.running"))
        return@launch
      }
      val backupManager = BackupManager.getInstance(project)
      if (!backupManager.isDeviceSupported(serialNumber)) {
        project.showDialog(message("error.device.not.supported"))
        return@launch
      }
      val isCompatible = actionHelper.checkCompatibleApps(project, serialNumber)
      if (!isCompatible) {
        project.showDialog(message("error.applications.not.installed"))
        return@launch
      }
      withContext(Dispatchers.EDT) {
        val file =
          (config as? Config.File)?.path ?: backupManager.chooseRestoreFile() ?: return@withContext
        backupManager.restoreModal(serialNumber, file, RESTORE_APP_ACTION, true)
      }
    }
  }

  private suspend fun getDeviceSerialNumber(e: AnActionEvent): String? {
    val project = e.project ?: return null
    return when (e.place) {
      "StreamingToolbarVirtualDevice" -> SERIAL_NUMBER_KEY.getData(e.dataContext)
      else -> actionHelper.getDeployTargetSerial(project)
    }
  }

  internal sealed class Config {
    data object Standalone : Config() {
      override val presentation =
        Presentation().apply {
          text = message("restore.action.title")
          description = message("restore.action.description")
          icon = AllIcons.Actions.Download
        }
    }

    data object Browse : Config() {
      override val presentation = Presentation(message("restore.action.browse"))
    }

    class File(val path: Path) : Config() {
      override val presentation = Presentation(path.pathString)
    }

    abstract val presentation: Presentation
  }

  private suspend fun Project.showDialog(message: String) {
    dialogFactory.showDialog(this@showDialog, message("restore.file.action.error.title"), message)
  }
}
