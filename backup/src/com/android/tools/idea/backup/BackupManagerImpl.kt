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

import com.android.adblib.DeviceSelector
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
import com.android.backup.ErrorCode.APP_NOT_INSTALLED
import com.android.backup.ErrorCode.BACKUP_NOT_ACTIVATED
import com.android.backup.ErrorCode.BACKUP_NOT_ENABLED
import com.android.backup.ErrorCode.BACKUP_NOT_SUPPORTED
import com.android.backup.ErrorCode.GMSCORE_IS_TOO_OLD
import com.android.backup.ErrorCode.PLAY_STORE_NOT_INSTALLED
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.validation.ErrorDetailDialog
import com.android.tools.environment.Logger
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.backup.BackupBundle.message
import com.android.tools.idea.backup.BackupFileType.FILE_CHOOSER_DESCRIPTOR
import com.android.tools.idea.backup.BackupManager.Companion.NOTIFICATION_GROUP
import com.android.tools.idea.backup.BackupManager.Source
import com.android.tools.idea.backup.DialogFactory.DialogButton
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
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
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation.Companion.cancellable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    DialogFactoryImpl(),
  )

  override suspend fun showBackupDialog(
    serialNumber: String,
    applicationId: String,
    source: Source,
    notify: Boolean,
  ) {
    val debuggableApps = getDebuggableApps(serialNumber)
    val isBackupEnabled =
      withContext(Dispatchers.Default) {
        debuggableApps.associateWith { backupService.isBackupEnabled(serialNumber, it) }
      }
    withContext(Dispatchers.EDT) {
      showBackupDialog(serialNumber, applicationId, debuggableApps, source, notify, isBackupEnabled)
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
    debuggableApps: List<String>,
    source: Source,
    notify: Boolean,
    isBackupEnabled: Map<String, Boolean>,
  ) {
    val dialog = BackupDialog(project, applicationId, debuggableApps, isBackupEnabled)
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
      result.notify(operation, serialNumber = serialNumber)
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

  override suspend fun isDeviceSupported(serialNumber: String): Boolean {
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    val deviceHandle =
      deviceProvisioner.findConnectedDeviceHandle(DeviceSelector.fromSerialNumber(serialNumber))
        ?: return false
    val deviceType = deviceHandle.state.properties.deviceType
    return deviceType == DeviceType.HANDHELD
  }

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
          result.notify(operation, backupFile, serialNumber)
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

  private suspend fun BackupResult.notify(
    operation: String,
    backupFile: Path? = null,
    serialNumber: String,
  ) {
    when (this) {
      is Success -> notifySuccess(message("notification.success", operation), backupFile)
      is WithoutAppData -> notifySuccessWithoutAppData(backupFile)
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

  private fun notifySuccessWithoutAppData(backupFile: Path?) {
    val notification =
      Notification(
        NOTIFICATION_GROUP,
        message("notification.success", message("backup")),
        message("notification.without.app.data"),
        INFORMATION,
      )
    notification.addAction(ShowPostBackupDialogAction(project, backupFile!!))
    notification.addAction(BackupDisabledLearnMoreAction())
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
}

private fun SequentialProgressReporter.onStep(step: Step) {
  nextStep(step.step * 100 / step.totalSteps, step.text)
}

private val ProcessHandler.applicationId: String?
  get() = getUserData(AndroidSessionInfo.KEY)?.applicationId
