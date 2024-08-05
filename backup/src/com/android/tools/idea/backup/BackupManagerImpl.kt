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

import com.android.annotations.concurrency.UiThread
import com.android.backup.BackupProgressListener
import com.android.backup.BackupProgressListener.Step
import com.android.backup.BackupResult
import com.android.backup.BackupResult.Error
import com.android.backup.BackupResult.Success
import com.android.backup.BackupService
import com.android.tools.adtui.validation.ErrorDetailDialog
import com.android.tools.environment.Logger
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupFileType.FILE_CHOOSER_DESCRIPTOR
import com.android.tools.idea.backup.BackupFileType.FILE_SAVER_DESCRIPTOR
import com.android.tools.idea.backup.BackupManager.Source
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent.Type.D2D
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
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
import org.jetbrains.annotations.VisibleForTesting

private const val BACKUP_PATH_KEY = "Backup.Path"
private const val NOTIFICATION_GROUP = "Backup"

private val logger: Logger = Logger.getInstance(BackupManager::class.java)

/** Implementation of [BackupManager] */
internal class BackupManagerImpl
@VisibleForTesting
internal constructor(private val project: Project, private val backupService: BackupService) :
  BackupManager {
  @Suppress("unused") // Used by the plugin XML
  constructor(
    project: Project
  ) : this(
    project,
    BackupService.getInstance(
      AdbLibService.getSession(project),
      logger,
      StudioFlags.BACKUP_GMSCORE_MIN_VERSION.get(),
    ),
  )

  @UiThread
  override fun backupModal(
    serialNumber: String,
    applicationId: String,
    backupFile: Path,
    source: Source,
    notify: Boolean,
  ): BackupResult {
    logger.debug("Backing up '$applicationId' from $backupFile on '${serialNumber}'")
    // TODO(348406593): Find a way to make the modal dialog be switched to background task
    return runWithModalProgressBlocking(
      ModalTaskOwner.project(project),
      message("backup"),
      cancellable(),
    ) {
      reportSequentialProgress { reporter ->
        val listener = BackupProgressListener(reporter::onStep)
        val result = backupService.backup(serialNumber, applicationId, backupFile, listener)
        val operation = message("backup")
        if (notify) {
          result.notify(operation, backupFile)
        }
        if (result is Error) {
          logger.warn(message("notification.error", operation), result.throwable)
        }
        BackupUsageTracker.logBackup(D2D, source, result)
        result
      }
    }
  }

  @UiThread
  override fun restoreModal(
    serialNumber: String,
    backupFile: Path,
    source: Source,
    notify: Boolean,
  ): BackupResult {
    // TODO(348406593): Find a way to make the modal dialog be switched to background task
    return runWithModalProgressBlocking(
      ModalTaskOwner.project(project),
      message("restore"),
      cancellable(),
    ) {
      reportSequentialProgress { reporter ->
        val listener = BackupProgressListener(reporter::onStep)
        restore(serialNumber, backupFile, source, listener, notify)
      }
    }
  }

  override suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    source: Source,
    listener: BackupProgressListener?,
    notify: Boolean,
  ): BackupResult {
    val path =
      when {
        backupFile.pathString.startsWith('/') -> backupFile
        else -> Path.of(project.basePath ?: "", backupFile.pathString)
      }
    logger.debug("Restoring from $path on '${serialNumber}'")
    val result = backupService.restore(serialNumber, backupFile, listener)
    val operation = message("restore")
    if (notify) {
      result.notify(operation)
    }
    if (result is Error) {
      logger.warn(message("notification.error", operation), result.throwable)
    }
    BackupUsageTracker.logRestore(source, result)
    return result
  }

  override suspend fun chooseBackupFile(nameHint: String): Path? {
    val path =
      FileChooserFactory.getInstance()
        .createSaveFileDialog(FILE_SAVER_DESCRIPTOR, project)
        .save(getBackupPath(), nameHint)
        ?.file
        ?.toPath()
    if (path != null) {
      setBackupPath(path)
    }
    return path
  }

  override fun chooseRestoreFile(): Path? {
    return FileChooserFactory.getInstance()
      .createFileChooser(FILE_CHOOSER_DESCRIPTOR, project, null)
      .choose(project)
      .firstOrNull()
      ?.toNioPath()
      ?.normalize()
  }

  override suspend fun getApplicationId(backupFile: Path): String? {
    try {
      return BackupService.validateBackupFile(backupFile)
    } catch (e: Exception) {
      logger.warn("File ${backupFile.pathString} is not a valid backup file")
      return null
    }
  }

  override fun getRestoreRunConfigSection(project: Project) = RestoreRunConfigSection(project)

  private fun BackupResult.notify(operation: String, backupFile: Path? = null) {
    when (this) {
      is Success -> notifySuccess(message("notification.success", operation), backupFile)
      is Error -> notifyError(message("notification.error", operation), throwable)
    }
  }

  private fun notifySuccess(message: String, backupFile: Path?) {
    val notification = Notification(NOTIFICATION_GROUP, message, INFORMATION)
    if (backupFile != null) {
      notification.addAction(RevealBackupFileAction(backupFile))
    }
    Notifications.Bus.notify(notification, project)
  }

  private fun notifyError(title: String, throwable: Throwable) {
    if (throwable is CancellationException) {
      return
    }
    val content = throwable.message ?: message("notification.unknown.error")
    val notification =
      Notification(NOTIFICATION_GROUP, title, content, WARNING)
        .addAction(ShowExceptionAction(title, content, throwable))
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

  private class RevealBackupFileAction(private val backupPath: Path) : RevealFileAction() {
    override fun actionPerformed(e: AnActionEvent) {
      openFile(backupPath)
    }
  }

  private class ShowExceptionAction(
    private val title: String,
    private val message: String,
    private val throwable: Throwable,
  ) : DumbAwareAction(message("notification.error.button")) {
    override fun actionPerformed(e: AnActionEvent) {
      ErrorDetailDialog(title, message, throwable.stackTraceToString()).show()
    }
  }
}

private fun SequentialProgressReporter.onStep(step: Step) {
  nextStep(step.step * 100 / step.totalSteps, step.text)
}
