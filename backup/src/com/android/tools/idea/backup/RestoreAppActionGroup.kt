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

import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.RestoreAppAction.Config.Browse
import com.android.tools.idea.backup.RestoreAppAction.Config.File
import com.android.tools.idea.backup.asyncaction.ActionEnableState
import com.android.tools.idea.backup.asyncaction.ActionEnableState.Disabled
import com.android.tools.idea.backup.asyncaction.ActionEnableState.Enabled
import com.android.tools.idea.backup.asyncaction.ActionGroupWithSuspendedUpdate
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** A Restore App popup [ActionGroup] containing recently used backup files and a browse action */
internal class RestoreAppActionGroup(private val actionHelper: ActionHelper = ActionHelperImpl()) :
  ActionGroupWithSuspendedUpdate() {

  override fun getActionUpdateThread() = BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    if (!StudioFlags.BACKUP_ENABLED.get()) {
      return
    }
    val project = e.project ?: return
    e.presentation.isPopupGroup = true
    e.presentation.isVisible = showGroup(project)
    e.presentation.isEnabled = false
    super.update(e)
  }

  override suspend fun suspendedUpdate(project: Project, e: AnActionEvent): ActionEnableState {
    if (
      (e.place == "MainToolbar" || e.place == "MainMenu") &&
        actionHelper.getDeployTargetCount(project) != 1
    ) {
      return Disabled(message("error.multiple.devices"))
    }
    val serialNumber =
      getDeviceSerialNumber(e) ?: return Disabled(message("error.device.not.running"))
    return when (actionHelper.checkCompatibleApps(project, serialNumber)) {
      true -> Enabled
      else -> Disabled(message("error.applications.not.installed"))
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return emptyArray()
    val files = BackupFileHistory(project).getFileHistory()
    return buildList {
        add(RestoreAppAction(Browse))
        files.forEach { add(RestoreAppAction(File(Path.of(it)))) }
      }
      .toTypedArray()
  }

  private suspend fun getDeviceSerialNumber(e: AnActionEvent): String? {
    val project = e.project ?: return null
    return SERIAL_NUMBER_KEY.getData(e.dataContext) ?: actionHelper.getDeployTargetSerial(project)
  }

  companion object {
    fun showGroup(project: Project) = BackupFileHistory(project).getFileHistory().isNotEmpty()
  }
}
