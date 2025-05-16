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
import com.android.backup.BackupMetadata
import com.android.backup.BackupProgressListener
import com.android.backup.BackupProgressListener.Step
import com.android.backup.BackupResult
import com.android.backup.BackupResult.Error
import com.android.backup.BackupResult.Success
import com.android.backup.BackupResult.WithoutAppData
import com.android.backup.BackupService
import com.android.backup.BackupType
import com.android.backup.ErrorCode.APP_NOT_DEBUGGABLE
import com.android.backup.ErrorCode.APP_NOT_INSTALLED
import com.android.backup.ErrorCode.BACKUP_NOT_ACTIVATED
import com.android.backup.ErrorCode.BACKUP_NOT_ENABLED
import com.android.backup.ErrorCode.BACKUP_NOT_SUPPORTED
import com.android.backup.ErrorCode.GMSCORE_IS_TOO_OLD
import com.android.backup.ErrorCode.PLAY_STORE_NOT_INSTALLED
import com.android.tools.adtui.validation.ErrorDetailDialog
import com.android.tools.environment.Logger
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupFileType.FILE_CHOOSER_DESCRIPTOR
import com.android.tools.idea.backup.BackupManager.Companion.NOTIFICATION_GROUP
import com.android.tools.idea.backup.BackupManager.Source
import com.android.tools.idea.backup.DialogFactory.DialogButton
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation.Companion.cancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.reportSequentialProgress
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

private val logger: Logger = Logger.getInstance(BackupManager::class.java)

/** Implementation of [BackupManager] */
internal class BackupManagerImpl
@VisibleForTesting
internal constructor(
  private val project: Project,
  private val backupService: BackupService,
  private val deviceChecker: DeviceChecker,
  private val dialogFactory: DialogFactory,
  private val virtualFileManager: VirtualFileManager = VirtualFileManager.getInstance(),
) : BackupManager {
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
    DeviceCheckerImpl(project),
    DialogFactoryImpl(),
  )

  @UiThread
  override fun showBackupDialog(
    serialNumber: String,
    applicationId: String?,
    source: Source,
    notify: Boolean,
  ) {
    val dialogData =
      runWithModalProgressBlocking(
        ModalTaskOwner.project(project),
        "Collecting Data",
        cancellable(),
      ) {
        reportSequentialProgress { reporter ->
          var steps = if (applicationId == null) 4 else 3
          var step = 0
          withContext(Default) {
            reporter.onStep(Step(++step, steps, "Checking device..."))
            if (!isDeviceSupported(serialNumber)) {
              project.showDialog(message("error.device.not.supported"))
              return@withContext null
            }

            val appId =
              when (applicationId) {
                null -> {
                  reporter.onStep(Step(++step, steps, "Detecting foreground app..."))
                  backupService.getForegroundApplicationId(serialNumber)
                }
                else -> applicationId
              }

            reporter.onStep(Step(++step, steps, "Detecting debuggable apps..."))
            val debuggableApps = backupService.getDebuggableApps(serialNumber)
            if (!debuggableApps.contains(appId)) {
              project.showDialog(message("error.application.not.debuggable", appId))
              return@withContext null
            }
            steps += debuggableApps.size - 1
            val appIdToBackupEnabledMap =
              withContext(Default) {
                debuggableApps.withIndex().associate {
                  reporter.onStep(Step(++step, steps, "Checking ${it.value}"))
                  it.value to backupService.isBackupEnabled(serialNumber, it.value)
                }
              }

            return@withContext DialogData(appId, appIdToBackupEnabledMap)
          }
        }
      }
    if (dialogData != null) {
      showBackupDialog(
        serialNumber,
        dialogData.applicationId,
        source,
        notify,
        dialogData.appIdToBackupEnabledMap,
      )
    }
  }

  override suspend fun getDebuggableApps(serialNumber: String): List<String> {
    return backupService.getDebuggableApps(serialNumber)
  }

  @VisibleForTesting
  @UiThread
  fun showBackupDialog(
    serialNumber: String,
    applicationId: String,
    source: Source,
    notify: Boolean,
    appIdToBackupEnabledMap: Map<String, Boolean>,
  ) {
    val dialog = BackupDialog(project, applicationId, appIdToBackupEnabledMap)
    val ok = dialog.showAndGet()
    if (ok) {
      doBackup(serialNumber, dialog.applicationId, dialog.type, dialog.backupPath, source, notify)
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
      result.notifyRestore(operation, serialNumber)
    }
    if (result is Error) {
      logger.warn(message("notification.error", operation), result.throwable)
    }
    BackupUsageTracker.logRestore(source, result)
    return result
  }

  @UiThread
  override fun chooseRestoreFile(): Path? {
    return FileChooserFactory.getInstance()
      .createFileChooser(FILE_CHOOSER_DESCRIPTOR, project, null)
      .choose(project)
      .firstOrNull()
      ?.toNioPath()
      ?.normalize()
  }

  override suspend fun getMetadata(backupFile: Path): BackupMetadata? {
    try {
      return BackupService.validateBackupFile(backupFile)
    } catch (_: Exception) {
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

  override suspend fun isDeviceSupported(serialNumber: String) =
    deviceChecker.isDeviceSupported(serialNumber)

  @UiThread
  @VisibleForTesting
  internal fun doBackup(
    serialNumber: String,
    applicationId: String,
    type: BackupType,
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
        val processes = ExecutionManager.getInstance(project).getRunningProcesses()
        processes.find { it.applicationId == applicationId }?.detachProcess()
        val listener = BackupProgressListener(reporter::onStep)
        val result = backupService.backup(serialNumber, applicationId, type, backupFile, listener)
        val operation = message("backup")
        if (notify) {
          result.notifyBackup(operation, backupFile, serialNumber, applicationId)
        }
        when (result) {
          is Success -> virtualFileManager.refreshAndFindFileByNioPath(backupFile)
          is WithoutAppData -> virtualFileManager.refreshAndFindFileByNioPath(backupFile)
          is Error -> logger.warn(message("notification.error", operation), result.throwable)
        }
        BackupUsageTracker.logBackup(type, source, result)
        result
      }
    }
  }

  private suspend fun BackupResult.notifyBackup(
    operation: String,
    backupFile: Path,
    serialNumber: String,
    applicationId: String,
  ) {
    when (this) {
      is Success ->
        notifyBackupSuccess(message("notification.success", operation), backupFile, applicationId)
      is WithoutAppData -> notifyBackupSuccessWithoutAppData(backupFile, applicationId)
      is Error -> notifyError(message("notification.error", operation), throwable, serialNumber)
    }
  }

  private suspend fun BackupResult.notifyRestore(operation: String, serialNumber: String) {
    when (this) {
      is Success -> notifyRestoreSuccess(message("notification.success", operation))
      is Error -> notifyError(message("notification.error", operation), throwable, serialNumber)
      is WithoutAppData ->
        throw IllegalArgumentException("Restore should not result in WithoutAppData")
    }
  }

  private fun notifyBackupSuccess(message: String, backupFile: Path, applicationId: String) {
    notify(message) {
      if (isAppInProject(applicationId)) {
        addAction(ShowPostBackupDialogAction(project, backupFile))
      }
    }
  }

  private fun notifyBackupSuccessWithoutAppData(backupFile: Path, applicationId: String) {
    notify(
      message("notification.without.app.data"),
      message("notification.success", message("backup")),
    ) {
      if (isAppInProject(applicationId)) {
        addAction(ShowPostBackupDialogAction(project, backupFile))
      }
      addAction(BackupDisabledLearnMoreAction())
    }
  }

  private fun notifyRestoreSuccess(message: String) {
    notify(message)
  }

  private fun notify(content: String, title: String = "", block: Notification.() -> Unit = {}) {
    val notification = Notification(NOTIFICATION_GROUP, title, content, INFORMATION)
    notification.block()
    Notifications.Bus.notify(notification, project)
  }

  private suspend fun notifyError(title: String, throwable: Throwable, serialNumber: String) {
    if (throwable is CancellationException) {
      return
    }
    val content = throwable.message ?: message("notification.unknown.error")
    val buttons = buildList {
      if (throwable.hasFullErrorDetail()) {
        add(
          DialogButton(message("notification.error.button")) {
            ErrorDetailDialog(title, content, throwable.stackTraceToString()).show()
          }
        )
      }
      val errorCode = (throwable as? BackupException)?.errorCode
      when (errorCode) {
        GMSCORE_IS_TOO_OLD -> addUpdateGmsCoreButton(serialNumber)
        BACKUP_NOT_ENABLED -> addBackupNotEnabledButton()
        else -> {}
      }
    }
    dialogFactory.showDialog(project, title, content, buttons)
  }

  private suspend fun MutableList<DialogButton>.addUpdateGmsCoreButton(serialNumber: String) {
    if (backupService.isPlayStoreInstalled(serialNumber)) {
      add(DialogButton(message("notification.update.gms")) { sendUpdateGmsIntent(serialNumber) })
    }
  }

  private fun MutableList<DialogButton>.addBackupNotEnabledButton() {
    add(DialogButton(message("learn.more")) { openBackupDisabledLearnMoreLink() })
  }

  private class ShowPostBackupDialogAction(val project: Project, private val backupPath: Path) :
    AnAction("Add to Run Configuration") {
    override fun actionPerformed(e: AnActionEvent) {
      PostBackupDialog(project, backupPath).show()
    }
  }

  private fun Throwable.hasFullErrorDetail(): Boolean {
    val code = (this as? BackupException)?.errorCode ?: return true
    return when (code) {
      BACKUP_NOT_SUPPORTED -> false
      BACKUP_NOT_ACTIVATED -> false
      APP_NOT_INSTALLED -> false
      APP_NOT_DEBUGGABLE -> false
      BACKUP_NOT_ENABLED -> false
      else -> true
    }
  }

  private fun sendUpdateGmsIntent(serialNumber: String) {
    AdbLibService.getSession(project).scope.launch {
      val result = backupService.sendUpdateGmsIntent(serialNumber)
      if (result is Error) {
        logger.warn("Error Updating Google Services", result.throwable)
        val message =
          when (result.errorCode) {
            PLAY_STORE_NOT_INSTALLED -> message("open.play.store.error.unavailable")
            else -> message("open.play.store.error.unexpected")
          }
        withContext(Dispatchers.EDT) {
          Messages.showErrorDialog(project, message, message("open.play.store.error.title"))
        }
      }
    }
  }

  private fun isAppInProject(applicationId: String) =
    project.getService(ProjectAppsProvider::class.java).getApplicationIds().contains(applicationId)

  companion object {
    fun openBackupDisabledLearnMoreLink() {
      BrowserUtil.browse("https://developer.android.com/identity/sign-in/restore-credentials")
    }
  }

  private class BackupDisabledLearnMoreAction : AnAction(message("learn.more")) {
    override fun actionPerformed(e: AnActionEvent) {
      openBackupDisabledLearnMoreLink()
    }
  }

  private fun Project.showDialog(message: String) {
    dialogFactory.showDialog(this@showDialog, message("backup.app.action.error.title"), message)
  }

  private class DialogData(
    val applicationId: String,
    val appIdToBackupEnabledMap: Map<String, Boolean>,
  )
}

private val ProcessHandler.applicationId: String?
  get() = getUserData(AndroidSessionInfo.KEY)?.applicationId
