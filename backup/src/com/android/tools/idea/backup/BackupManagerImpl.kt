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

import com.android.backup.BackupHandler
import com.android.backup.BackupProgressListener
import com.android.backup.BackupProgressListener.Step
import com.android.backup.BackupResult
import com.android.backup.BackupResult.Error
import com.android.backup.BackupResult.Success
import com.android.backup.RestoreHandler
import com.android.tools.environment.Logger
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.notification.Notifications
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation.cancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private const val BACKUP_PATH_KEY = "Backup.Path"
private const val BACKUP_EXT = "backup"
private const val NOTIFICATION_GROUP = "Backup"

/** Implementation of [BackupManager] */
internal class BackupManagerImpl(private val project: Project) : BackupManager {
  private val adbSession = AdbLibService.getSession(project)
  private val logger: Logger = Logger.getInstance(this::class.java)

  override suspend fun backup(serialNumber: String, applicationId: String, backupFile: Path) {
    logger.debug("Backing up '$applicationId' from $backupFile on '${serialNumber}'")
    // TODO(348406593): Find a way to make the modal dialog be switched to background task
    runWithModalProgressBlocking(
      ModalTaskOwner.project(project),
      message("backup"),
      cancellable(),
    ) {
      reportSequentialProgress { reporter ->
        val listener = BackupProgressListener(reporter::onStep)
        val handler =
          BackupHandler(adbSession, serialNumber, logger, listener, backupFile, applicationId)
        val result = handler.backup()
        result.notify(message("backup"))
      }
    }
  }

  override suspend fun restore(serialNumber: String, backupFile: Path) {
    logger.debug("Restoring from $backupFile on '${serialNumber}'")
    // TODO(348406593): Find a way to make the modal dialog be switched to background task
    runWithModalProgressBlocking(
      ModalTaskOwner.project(project),
      message("restore"),
      cancellable(),
    ) {
      reportSequentialProgress { reporter ->
        val listener = BackupProgressListener(reporter::onStep)
        val handler = RestoreHandler(adbSession, logger, serialNumber, listener, backupFile)
        val result = handler.restore()
        result.notify(message("restore"))
      }
    }
  }

  override suspend fun chooseBackupFile(nameHint: String): Path? {
    val dialog =
      FileChooserFactory.getInstance()
        .createSaveFileDialog(
          FileSaverDescriptor(message("backup.choose.backup.file.dialog.title"), "", BACKUP_EXT),
          project,
        )
    val path = dialog.save(getBackupPath(), nameHint)?.file?.toPath()
    if (path != null) {
      setBackupPath(path)
    }
    return path
  }

  override fun chooseRestoreFile(): Path? {
    val descriptor =
      FileChooserDescriptor(true, false, true, true, false, false)
        .withTitle(message("backup.choose.restore.file.dialog.title"))
        .withFileFilter { it.name.endsWith(".$BACKUP_EXT") }
    return FileChooserFactory.getInstance()
      .createFileChooser(descriptor, project, null)
      .choose(project)
      .firstOrNull()
      ?.toNioPath()
      ?.normalize()
  }

  private fun BackupResult.notify(operation: String) {
    when (this) {
      is Success -> notifySuccess(message("notification.success", operation))
      is Error -> notifyError(message("notification.error", operation), throwable)
    }
  }

  private fun notifySuccess(message: String) {
    val notification = Notification(NOTIFICATION_GROUP, message, INFORMATION)
    Notifications.Bus.notify(notification, project)
  }

  private fun notifyError(message: String, throwable: Throwable) {
    if (throwable is CancellationException) {
      return
    }
    val notification = Notification(NOTIFICATION_GROUP, message, WARNING)
    logger.warn(message, throwable)
    Notifications.Bus.notify(notification, project)
  }

  private suspend fun getBackupPath(): Path? {
    val properties = PropertiesComponent.getInstance(project)
    return withContext(AndroidDispatchers.diskIoThread) {
      when (val lastPath = properties.getValue(BACKUP_PATH_KEY)) {
        null -> project.guessProjectDir()?.toNioPath()
        else -> LocalFileSystem.getInstance().findFileByPath(lastPath)?.toNioPath()
      }
    }
  }

  private fun setBackupPath(path: Path) {
    PropertiesComponent.getInstance(project).setValue(BACKUP_PATH_KEY, path.pathString)
  }
}

private fun SequentialProgressReporter.onStep(step: Step) {
  nextStep(step.step * 100 / step.totalSteps, step.text)
}
