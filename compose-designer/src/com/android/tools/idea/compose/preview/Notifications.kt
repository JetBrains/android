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

import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import java.awt.Color

private fun createBuildNotificationPanel(project: Project,
                                         file: VirtualFile,
                                         text: String,
                                         buildActionLabel: String = message("notification.action.build"),
                                         color: Color? = null): EditorNotificationPanel? {
  val module = ModuleUtil.findModuleForFile(file, project) ?: return null
  return EditorNotificationPanel(color).apply {
    setText(text)

    createActionLabel(buildActionLabel) {
      requestBuild(project, module)
    }
  }
}

/**
 * [EditorNotifications.Provider] that displays the notification when the preview needs to be refreshed.
 */
class ComposePreviewNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.notification")
  private val LOG = Logger.getInstance(ComposePreviewNotificationProvider::class.java)

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    LOG.debug("createNotificationsProvider")
    val previewStatus = fileEditor.getComposePreviewManager()?.status() ?: return null
    if (LOG.isDebugEnabled) {
      LOG.debug(previewStatus.toString())
    }

    // Show a notification with a Loader if the preview is refreshing.
    if (previewStatus.isRefreshing) {
      LOG.debug("Refresh in progress")
      return EditorNotificationPanel().apply {
        setText(message("notification.preview.is.refreshing"))
        icon(AnimatedIcon.Default())
      }
    }

    val gradleBuildState = GradleBuildState.getInstance(project)
    // Do not show the notification while the build is in progress but refresh is not.
    if (gradleBuildState.isBuildInProgress) {
      LOG.debug("Build in progress")
      return null
    }

    val status = GradleBuildState.getInstance(project)?.summary?.status
    val lastBuildSuccessful = status == BuildStatus.SKIPPED || status == BuildStatus.SUCCESS

    return when {
      // Check if the project has compiled correctly
      !lastBuildSuccessful -> createBuildNotificationPanel(
        project,
        file,
        text = message("notification.needs.build.broken"),
        color = LightColors.RED)

      previewStatus.isOutOfDate -> createBuildNotificationPanel(
        project,
        file,
        text = message("notification.preview.out.of.date"),
        buildActionLabel = message("notification.action.build.and.refresh"))

      // If the project has compiled, it could be that we are missing a class because we need to recompile.
      // Check for errors from missing classes
      previewStatus.hasErrors -> createBuildNotificationPanel(
        project,
        file,
        text = if (previewStatus.hasSyntaxErrors) message("notification.syntax.errors") else message("notification.needs.build"),
        color = LightColors.RED)

      else -> null
    }
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}