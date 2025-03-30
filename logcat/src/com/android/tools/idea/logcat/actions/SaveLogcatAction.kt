/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.files.LogcatFileIo
import com.android.tools.idea.logcat.util.LOGGER
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.run.ShowLogcatListener
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SAVE_PATH_KEY = "Logcat.SavePath"

private const val LOGCAT_EXT = "logcat"

internal class SaveLogcatAction :
  DumbAwareAction(
    LogcatBundle.message("logcat.save.log.action.text"),
    null,
    AllIcons.ToolbarDecorator.Export,
  ) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled =
      e.getLogcatPresenter()?.let {
        it.getSelectedDevice() != null && it.getBacklogMessages().isNotEmpty()
      } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val logcatPresenter = e.getLogcatPresenter() ?: return
    val device = logcatPresenter.getSelectedDevice() ?: return

    val dialog =
      FileChooserFactory.getInstance()
        .createSaveFileDialog(
          FileSaverDescriptor(LogcatBundle.message("logcat.save.log.dialog.title"), "", LOGCAT_EXT),
          project,
        )

    val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT).format(Date())
    val deviceName = device.name.replace(' ', '-')
    val systemInfo = "Android-${device.release}"
    val filename = "${deviceName}-${systemInfo}_${timestamp}".adjustedForMac()
    val file = dialog.save(getSavePath(project), filename)?.file ?: return
    PropertiesComponent.getInstance(project).setValue(SAVE_PATH_KEY, file.parent)

    val logcatMessages = logcatPresenter.getBacklogMessages()
    val filter = logcatPresenter.getFilter()

    val projectApplicationIds =
      project.getService(ProjectApplicationIdsProvider::class.java)?.getPackageNames() ?: emptySet()

    AndroidCoroutineScope(logcatPresenter, AndroidDispatchers.diskIoThread).launch {
      LogcatFileIo()
        .writeLogcat(file.toPath(), logcatMessages, device, filter, projectApplicationIds)
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
      if (virtualFile == null) {
        LOGGER.warn("Failed to save Logcat file: $file")
        return@launch
      }
      val notification =
        Notification(
            "Logcat",
            LogcatBundle.message("logcat.save.log.notification.text"),
            NotificationType.INFORMATION,
          )
          .addAction(OpenInEditorAction(virtualFile))
          .addAction(RevealLogcatFileAction(virtualFile))
          .addAction(OpenInLogcatAction(virtualFile, "${device.name} Android ${device.release}"))
      Notifications.Bus.notify(notification, project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = EDT

  private fun getSavePath(project: Project): VirtualFile? {
    val properties = PropertiesComponent.getInstance(project)
    val lastPath = properties.getValue(SAVE_PATH_KEY)
    return if (lastPath != null) {
      LocalFileSystem.getInstance().findFileByPath(lastPath)
    } else project.guessProjectDir()
  }

  private class OpenInEditorAction(val file: VirtualFile) :
    DumbAwareAction(LogcatBundle.message("logcat.save.log.notification.open.in.editor")) {
    override fun getActionUpdateThread() = EDT

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  private class RevealLogcatFileAction(val file: VirtualFile) :
    DumbAwareAction(RevealFileAction.getActionName()) {
    override fun getActionUpdateThread() = EDT

    override fun actionPerformed(e: AnActionEvent) {
      RevealFileAction.openFile(file.toNioPath())
    }
  }

  private class OpenInLogcatAction(val file: VirtualFile, private val tabName: String) :
    DumbAwareAction(LogcatBundle.message("logcat.save.log.notification.open.in.logcat")) {
    override fun getActionUpdateThread() = EDT

    override fun actionPerformed(e: AnActionEvent) {
      e.project
        ?.messageBus
        ?.syncPublisher(ShowLogcatListener.TOPIC)
        ?.showLogcatFile(file.toNioPath(), tabName)
    }
  }
}

private fun String.adjustedForMac(): String {
  // Add extension to filename on Mac only see: http://b/38447816.
  return if (SystemInfo.isMac) "$this.$LOGCAT_EXT" else this
}
