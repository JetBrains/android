/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors

/**
 * [EditorNotifications.Provider] that displays the notification when the preview needs to be refreshed.
 */
class OutdatedPreviewNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.outdated")

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    val previewManager = fileEditor.getComposePreviewManager() ?: return null

    // Do not show the notification while the build is in progress
    if (GradleBuildState.getInstance(project).isBuildInProgress) return null

    // Check for errors from missing classes
    if (previewManager.needsBuild()) {
      val module = ModuleUtil.findModuleForFile(file, project) ?: return null
      return EditorNotificationPanel(LightColors.RED).apply {
        setText("The project needs to be compiled for the preview to be displayed")

        createActionLabel("Build", Runnable {
          requestBuild(project, module)
        })
      }
    }

    val isModified = FileDocumentManager.getInstance().isFileModified(file)
    if (!isModified) {
      // The file was saved, check the compilation time
      val modificationStamp = file.timeStamp
      val lastBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).lastBuildTimestamp ?: -1
      if (lastBuildTimestamp < 0L || lastBuildTimestamp >= modificationStamp) return null
    }

    val module = ModuleUtil.findModuleForFile(file, project) ?: return null
    return EditorNotificationPanel().apply {
      setText("The preview is out of date")

      createActionLabel("Refresh", Runnable {
        requestBuild(project, module)
      })
    }
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}