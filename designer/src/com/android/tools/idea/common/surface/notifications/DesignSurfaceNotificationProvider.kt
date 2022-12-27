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
package com.android.tools.idea.common.surface.notifications

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.editor.NlEditor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/**
 * A [EditorNotificationProvider] for the [DesignSurface].
 */
class DesignSurfaceNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    return Function { createNotificationPanel(file, it, project) }
  }

  private fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    val surface: DesignSurface<*> = (fileEditor as? NlEditor)?.component?.surface ?: return null
    return if (!surface.isRefreshing && surface.sceneManagers.any { it.isOutOfDate }) {
      EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
        text = "The preview is out of date"
        createActionLabel("Refresh") {
          surface.forceUserRequestedRefresh()
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    } else null
  }
}