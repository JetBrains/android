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

import com.android.tools.idea.compose.preview.util.FilePreviewElementFinder
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.requestBuild
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.TimeUnit

/**
 * [EditorNotifications.Provider] that displays the notification when a Kotlin file adds the preview
 * import. The notification will close the current editor and open one with the preview.
 */
internal class ComposeNewPreviewNotificationProvider
@NonInjectable
constructor(private val filePreviewElementProvider: () -> FilePreviewElementFinder) :
  EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY =
    Key.create<EditorNotificationPanel>("android.tools.compose.preview.new.notification")

  constructor() : this(::defaultFilePreviewElementFinder)

  override fun createNotificationPanel(
    file: VirtualFile,
    fileEditor: FileEditor,
    project: Project
  ): EditorNotificationPanel? =
    when {
      StudioFlags.NELE_SOURCE_CODE_EDITOR.get() -> null
      // Not a Kotlin file or already a Compose Preview Editor
      !file.isKotlinFileType() || fileEditor.getComposePreviewManager() != null -> null
      filePreviewElementProvider().hasPreviewMethods(project, file) ->
        EditorNotificationPanel(fileEditor).apply {
          setText(message("notification.new.preview"))
          createActionLabel(message("notification.new.preview.action")) {
            if (fileEditor.isValid) {
              FileEditorManager.getInstance(project).closeFile(file)
              FileEditorManager.getInstance(project).openFile(file, true)
              project.requestBuild(file)
            }
          }
        }
      else -> null
    }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}

/**
 * [ProjectComponent] that listens for Kotlin file additions or removals and triggers a notification
 * update
 */
internal class ComposeNewPreviewNotificationManager(private val project: Project) {
  private val LOG = Logger.getInstance(ComposeNewPreviewNotificationManager::class.java)

  private val updateNotificationQueue: MergingUpdateQueue by lazy {
    MergingUpdateQueue(
      "Update notifications",
      TimeUnit.SECONDS.toMillis(2).toInt(),
      true,
      null,
      project
    )
  }

  init {
    StartupManager.getInstance(project).runAfterOpened {
      projectOpened(project)
    }
  }

  private fun projectOpened(project: Project) {
    LOG.debug("projectOpened")

    PsiManager.getInstance(project)
      .addPsiTreeChangeListener(
        object : PsiTreeChangeAdapter() {
          private fun onEvent(event: PsiTreeChangeEvent) {
            val file = event.file?.virtualFile ?: return
            if (!file.isKotlinFileType()) return
            updateNotificationQueue.queue(
              object : Update(file) {
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
              }
            )
          }

          override fun childAdded(event: PsiTreeChangeEvent) {
            onEvent(event)
          }

          override fun childRemoved(event: PsiTreeChangeEvent) {
            onEvent(event)
          }
        },
        project
      )
  }
}
