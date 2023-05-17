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
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAVE_PATH_KEY = "Logcat.SavePath"

internal class SaveLogcatAction : DumbAwareAction(LogcatBundle.message("logcat.save.log.action.text"), null, AllIcons.Actions.MenuSaveall) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getLogcatPresenter()?.let {
      it.getSelectedDevice() != null && it.getBacklogMessages().isNotEmpty()
    } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val logcatPresenter = e.getLogcatPresenter() ?: return
    val device = logcatPresenter.getSelectedDevice() ?: return

    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(
      FileSaverDescriptor(LogcatBundle.message("logcat.save.log.dialog.title"), "", "logcat"),
      project)
    val filename = "${device.name.replace(' ', '-')}-Android-${device.release}"
    val file = dialog.save(getSavePath(project), filename)?.file ?: return
    PropertiesComponent.getInstance(project).setValue(SAVE_PATH_KEY, file.parent)

    val logcatMessages = logcatPresenter.getBacklogMessages()
    val filter = logcatPresenter.getFilter()

    val projectApplicationIds = project.getService(ProjectApplicationIdsProvider::class.java)?.getPackageNames() ?: emptySet()

    AndroidCoroutineScope(logcatPresenter, AndroidDispatchers.diskIoThread).launch {
      LogcatFileIo.writeLogcat(file.toPath(), logcatMessages, device, filter, projectApplicationIds)
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
      if (virtualFile == null) {
        LOGGER.warn("Failed to save Logcat file: $file")
        return@launch
      }
      withContext(AndroidDispatchers.uiThread) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = EDT

  private fun getSavePath(project: Project): VirtualFile? {
    val properties = PropertiesComponent.getInstance(project)
    val lastPath = properties.getValue(SAVE_PATH_KEY)
    return if (lastPath != null) {
      LocalFileSystem.getInstance().findFileByPath(lastPath)
    }
    else project.guessProjectDir()
  }
}
