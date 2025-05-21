/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.importer.wfs

import com.android.SdkConstants.EXT_APP_BUNDLE
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.MISSING_MAIN_MODULE
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.UNKNOWN
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOTIFICATION_GROUP = "Wear Declarative Watch Faces"

/**
 * This action allows users to import `.aab` files produced by
 * [Watch Face Studio](https://developer.samsung.com/watch-face-studio/overview.html).
 *
 * @see [WatchFaceStudioFileImporter]
 */
class ImportWatchFaceStudioFileAction(
  private val defaultDispatcher: CoroutineDispatcher,
  private val edtDispatcher: CoroutineContext,
) : DumbAwareAction() {

  constructor() : this(defaultDispatcher = Dispatchers.Default, edtDispatcher = Dispatchers.EDT)

  private val descriptor =
    FileChooserDescriptor(true, false, false, false, false, false)
      .withExtensionFilter(EXT_APP_BUNDLE)

  private val notificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible =
      StudioFlags.WATCH_FACE_STUDIO_FILE_IMPORT.get() && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val fileToImport = FileChooser.chooseFile(descriptor, null, e.project, null) ?: return

    project.coroutineScope.launch(defaultDispatcher) {
      val result =
        withBackgroundProgress(project, message("wfs.import.progress")) {
          WatchFaceStudioFileImporter.getInstance(project).import(fileToImport.toNioPath())
        }

      withContext(edtDispatcher) {
        when (result) {
          is WFSImportResult.Success -> notifySuccess(project)
          is WFSImportResult.Error -> notifyError(project, result.error)
        }
      }
    }
  }

  private fun notifySuccess(project: Project) {
    notificationGroup
      .createNotification(message("wfs.import.success"), NotificationType.INFORMATION)
      .notify(project)
  }

  private fun notifyError(project: Project, errorType: WFSImportResult.Error.Type) {
    val message =
      when (errorType) {
        UNKNOWN -> message("wfs.import.error.generic.message")
        MISSING_MAIN_MODULE -> message("wfs.import.error.missing.main.module.message")
      }
    notificationGroup.createNotification(message, NotificationType.ERROR).notify(project)
  }
}
