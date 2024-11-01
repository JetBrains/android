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
import com.android.backup.BackupException
import com.android.backup.BackupProgressListener
import com.android.backup.BackupProgressListener.Step
import com.android.backup.BackupResult
import com.android.backup.BackupResult.Error
import com.android.backup.BackupResult.Success
import com.android.backup.BackupService
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.backup.ErrorCode
import com.android.backup.ErrorCode.PLAY_STORE_NOT_INSTALLED
import com.android.tools.adtui.validation.ErrorDetailDialog
import com.android.tools.environment.Logger
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupFileType.FILE_CHOOSER_DESCRIPTOR
import com.android.tools.idea.backup.BackupManager.Companion.NOTIFICATION_GROUP
import com.android.tools.idea.backup.BackupManager.Source
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.flags.StudioFlags
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation.Companion.cancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

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
  override fun showBackupDialog(
    serialNumber: String,
    applicationId: String,
    source: Source,
    notify: Boolean,
  ) {
    val dialog = BackupDialog(project, applicationId)
    val ok = dialog.showAndGet()
    if (ok) {
      doBackup(serialNumber, applicationId, dialog.backupPath, source, notify)
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
        backupFile.isAbsolute -> backupFile
        else -> Path.of(project.basePath ?: "", backupFile.pathString)
      }
    logger.debug("Restoring from $path on '${serialNumber}'")
    val result = backupService.restore(serialNumber, path, listener)
    val operation = message("restore")
    if (notify) {
      result.notify(operation, serialNumber = serialNumber)
    }
    if (result is Error) {
      logger.warn(message("notification.error", operation), result.throwable)
    }
    BackupUsageTracker.logRestore(source, result)
    return result
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

  override suspend fun getForegroundApplicationId(serialNumber: String): String {
    return backupService.getForegroundApplicationId(serialNumber)
  }

  override suspend fun isInstalled(serialNumber: String, applicationId: String): Boolean {
    return backupService.isInstalled(serialNumber, applicationId)
  }

  @UiThread
  @VisibleForTesting
  internal fun doBackup(
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
        // TODO: Support different backup types. Probably change this method name to
        // `showBackupDialog()` and make it handle
        //  the type, file and any other parameters it might need.
        val result =
          backupService.backup(serialNumber, applicationId, DEVICE_TO_DEVICE, backupFile, listener)
        val operation = message("backup")
        if (notify) {
          result.notify(operation, backupFile, serialNumber)
        }
        if (result is Error) {
          logger.warn(message("notification.error", operation), result.throwable)
        }
        BackupUsageTracker.logBackup(DEVICE_TO_DEVICE, source, result)
        result
      }
    }
  }

  private fun BackupResult.notify(
    operation: String,
    backupFile: Path? = null,
    serialNumber: String,
  ) {
    when (this) {
      is Success -> notifySuccess(message("notification.success", operation), backupFile)
      is Error -> notifyError(message("notification.error", operation), throwable, serialNumber)
    }
  }

  private fun notifySuccess(message: String, backupFile: Path?) {
    val notification = Notification(NOTIFICATION_GROUP, message, INFORMATION)
    if (backupFile != null) {
      notification.addAction(ShowPostBackupDialogAction(project, backupFile))
    }
    Notifications.Bus.notify(notification, project)
  }

  private fun notifyError(title: String, throwable: Throwable, serialNumber: String) {
    if (throwable is CancellationException) {
      return
    }
    val content = throwable.message ?: message("notification.unknown.error")
    val notification =
      Notification(NOTIFICATION_GROUP, title, content, WARNING)
        .addAction(ShowExceptionAction(title, content, throwable))
    if ((throwable as? BackupException)?.errorCode == ErrorCode.GMSCORE_IS_TOO_OLD) {
      notification.addAction(UpdateGmsAction(serialNumber))
    }
    Notifications.Bus.notify(notification, project)
  }

  private class ShowPostBackupDialogAction(val project: Project, private val backupPath: Path) :
    AnAction("Add to Run Configuration") {
    override fun actionPerformed(e: AnActionEvent) {
      PostBackupDialog(project, backupPath).show()
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

  private inner class UpdateGmsAction(private val serialNumber: String) :
    DumbAwareAction(message("notification.update.gms")) {
    override fun actionPerformed(e: AnActionEvent) {
      AdbLibService.getSession(project).scope.launch {
        val result = backupService.sendUpdateGmsIntent(serialNumber)
        if (result is Error) {
          logger.warn("Error Updating Google Services", result.throwable)
          val message =
            when (result.errorCode) {
              PLAY_STORE_NOT_INSTALLED -> message("open.play.store.error.unavailable")
              else -> message("open.play.store.error.unexpected")
            }
          withContext(uiThread) {
            @Suppress("UnstableApiUsage")
            MessagesService.getInstance()
              .showErrorDialog(project, message, message("open.play.store.error.title"))
          }
        }
      }
    }
  }
}

private fun SequentialProgressReporter.onStep(step: Step) {
  nextStep(step.step * 100 / step.totalSteps, step.text)
}
