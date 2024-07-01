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

import com.android.ddmlib.IDevice
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.RestoreFileAction.RestoreInfo.Invalid
import com.android.tools.idea.backup.RestoreFileAction.RestoreInfo.Valid
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags.BACKUP_SHOW_RESTORE_ACTION_IN_PROJECT_VIEW
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetContext
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.CommonBundle
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restores an Android Application from a backup file
 *
 * TODO(b/348406593): Add tests
 */
internal class RestoreFileAction : AnAction() {
  override fun getActionUpdateThread() = BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    if (!BACKUP_SHOW_RESTORE_ACTION_IN_PROJECT_VIEW.get()) {
      return
    }
    val virtualFile = e.getData(VIRTUAL_FILE) ?: return
    if (virtualFile.fileType != BackupFileType) {
      return
    }
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    project.coroutineScope().launch {
      when (val restoreInfo = e.getRestoreInfo()) {
        is Invalid -> showWarning(project, restoreInfo.reason)
        is Valid -> restoreInfo.restore(project)
      }
    }
  }

  private suspend fun AnActionEvent.getRestoreInfo(): RestoreInfo {
    val project = project ?: throw IllegalStateException("Missing project")
    val packageName =
      project.getPackageName() ?: return Invalid(message("error.incompatible.run.config"))
    val backupFile =
      getData(VIRTUAL_FILE)?.toNioPath() ?: throw IllegalStateException("Missing file")
    val backupManager = BackupManager.getInstance(project)

    // Check application id
    val applicationId = backupManager.getApplicationId(backupFile)
    when {
      applicationId == null -> return Invalid(message("error.invalid.file", backupFile.pathString))
      applicationId != packageName ->
        return Invalid(message("error.wrong.package", applicationId, packageName))
    }

    val devices = project.getDevices()
    when {
      devices.isEmpty() -> return Invalid(message("error.device.not.running"))
      devices.size > 1 -> return Invalid(message("error.multiple.devices"))
    }

    // TODO(b/348406593) Validate GMS version

    return Valid(backupFile, devices.first().await().serialNumber)
  }

  private suspend fun showWarning(project: Project, message: String) {
    withContext(uiThread) {
      @Suppress("UnstableApiUsage")
      MessageDialogBuilder.Message(message("restore.file.action.error.title"), message)
        .buttons(CommonBundle.getOkButtonText())
        .asWarning()
        .show(project)
    }
  }

  private sealed class RestoreInfo {
    class Valid(val backupFile: Path, val serialNumber: String) : RestoreInfo()

    class Invalid(val reason: String) : RestoreInfo()
  }

  private suspend fun Valid.restore(project: Project) {
    withContext(uiThread) {
      BackupManager.getInstance(project).restoreModal(serialNumber, backupFile)
    }
  }
}

private fun Project.getPackageName(): String? {
  val runManager = RunManager.getInstance(this)
  val runConfig = runManager.selectedConfiguration?.configuration ?: return null
  return getProjectSystem().getApplicationIdProvider(runConfig)?.packageName
}

private fun Project.getDevices(): List<ListenableFuture<IDevice>> {
  val deployTarget = getDeployTarget() ?: return emptyList()
  return deployTarget.getAndroidDevices(this).filter { it.isRunning }.map { it.launchedDevice }
}

fun Project.getDeployTarget(): DeployTarget? {
  val targetProvider: DeployTargetProvider = DeployTargetContext().getCurrentDeployTargetProvider()
  return when (targetProvider.requiresRuntimePrompt(this)) {
    true -> null
    false -> targetProvider.getDeployTarget(this)
  }
}
