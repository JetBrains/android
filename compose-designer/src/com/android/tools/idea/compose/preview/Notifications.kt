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

import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.compose.preview.util.isKotlinFileType
import com.android.tools.idea.compose.preview.util.requestBuild
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.awt.Color
import java.util.concurrent.TimeUnit

private fun createBuildNotificationPanel(project: Project,
                                         file: VirtualFile,
                                         text: String,
                                         buildActionLabel: String = "${message(
                                           "notification.action.build")}${getBuildAndRefreshShortcut().asString()}",
                                         color: Color? = null): EditorNotificationPanel? {
  val module = ModuleUtil.findModuleForFile(file, project) ?: return null
  return EditorNotificationPanel(color).apply {
    setText(text)
    isFocusable = false

    createActionLabel(buildActionLabel) {
      requestBuild(project, module)
    }
  }
}


/**
 * [EditorNotifications.Provider] that displays the notification when a Kotlin file adds the preview import. The notification will close
 * the current editor and open one with the preview.
 */
internal class ComposeNewPreviewNotificationProvider @NonInjectable constructor(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder) : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.new.notification")

  constructor(): this(::defaultFilePreviewElementFinder)

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? =
    when {
      StudioFlags.NELE_SOURCE_CODE_EDITOR.get() -> null
      !StudioFlags.COMPOSE_PREVIEW.get() -> null
      // Not a Kotlin file or already a Compose Preview Editor
      !file.isKotlinFileType() || fileEditor.getComposePreviewManager() != null -> null
      filePreviewElementProvider().hasPreviewMethods(project, file) -> EditorNotificationPanel(fileEditor).apply {
        setText(message("notification.new.preview"))
        createActionLabel(message("notification.new.preview.action")) {
          if (fileEditor.isValid) {
            FileEditorManager.getInstance(project).closeFile(file)
            FileEditorManager.getInstance(project).openFile(file, true)
            val module = ModuleUtil.findModuleForFile(file, project) ?: return@createActionLabel
            requestBuild(project, module)
          }
        }
      }
      else -> null
    }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}

/**
 * [ProjectComponent] that listens for Kotlin file additions or removals and triggers a notification update
 */
internal class ComposeNewPreviewNotificationManager(private val project: Project) : ProjectComponent {
  private val LOG = Logger.getInstance(ComposeNewPreviewNotificationManager::class.java)

  private val updateNotificationQueue: MergingUpdateQueue by lazy {
    MergingUpdateQueue("Update notifications",
                       TimeUnit.SECONDS.toMillis(2).toInt(),
                       true,
                       null,
                       project)
  }

  override fun projectOpened() {
    if (!StudioFlags.COMPOSE_PREVIEW.get()) {
      return
    }
    LOG.debug("projectOpened")

    PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      private fun onEvent(event: PsiTreeChangeEvent) {
        val file = event.file?.virtualFile ?: return
        if (!file.isKotlinFileType()) return
        updateNotificationQueue.queue(object : Update(file) {
          override fun run() {
            if (project.isDisposed || !file.isValid) {
              return
            }

            if (LOG.isDebugEnabled) {
              LOG.debug("updateNotifications for ${file.name}")
            }

            if (FileEditorManager.getInstance(project).getEditors(file).isEmpty()) {
              LOG.debug("No editor found")
              return
            }

            EditorNotifications.getInstance(project).updateNotifications(file)
          }
        })
      }

      override fun childAdded(event: PsiTreeChangeEvent) {
        onEvent(event)
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        onEvent(event)
      }
    }, project)
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
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return null
    }

    val previewManager = fileEditor.getComposePreviewManager() ?: return null
    val previewStatus = previewManager.status() ?: return null
    if (LOG.isDebugEnabled) {
      LOG.debug(previewStatus.toString())
    }

    // Show a notification with a Loader if the preview is refreshing.
    if (previewStatus.isRefreshing) {
      LOG.debug("Refresh in progress")
      return EditorNotificationPanel(fileEditor).apply {
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

    val status = GradleBuildState.getInstance(project).summary?.status
    // If there was no build or the project is loading, we won't have a status. We do not consider that as a build failure yet.
    val lastBuildSuccessful = status == null || status == BuildStatus.SKIPPED || status == BuildStatus.SUCCESS

    return when {
      // Check if the project has compiled correctly
      !lastBuildSuccessful -> createBuildNotificationPanel(
        project,
        file,
        text = message("notification.needs.build.broken"),
        color = LightColors.RED)

      // If the preview is out of date and auto-build is not enabled, display the notification explaining the user they need to refresh.
      previewStatus.isOutOfDate && !previewManager.isAutoBuildEnabled -> createBuildNotificationPanel(
        project,
        file,
        text = message("notification.preview.out.of.date"),
        buildActionLabel = "${message("notification.action.build.and.refresh")}${getBuildAndRefreshShortcut().asString()}")

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