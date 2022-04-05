/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.idea.compose.preview.ComposePreviewNotificationProvider
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.notification.EventLog
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors

class FastPreviewDisableNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val componentKey = Key.create<EditorNotificationPanel>("android.tools.compose.preview.fast.notification")
  private val log = Logger.getInstance(ComposePreviewNotificationProvider::class.java)

  override fun getKey(): Key<EditorNotificationPanel> = componentKey

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (!StudioFlags.COMPOSE_FAST_PREVIEW.get()) return null
    log.debug("createNotificationPanel")
    val disableReason = FastPreviewManager.getInstance(project).disableReason ?: return null
    if (disableReason == ManualDisabledReason) return null
    return EditorNotificationPanel(LightColors.RED).apply {
      text = disableReason.title
      isFocusable = false

      if (disableReason.hasLongDescription) {
        createActionLabel(message("fast.preview.disabled.notification.show.details.action.title")) {
          EventLog.getEventLog(project)?.activate(null)
        }
      }
      createActionLabel(message("fast.preview.disabled.notification.reenable.action.title")) {
        FastPreviewManager.getInstance(project).enable()
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
      createActionLabel(message("fast.preview.disabled.notification.stop.autodisable.action.title")) {
        FastPreviewManager.getInstance(project).allowAutoDisable = false
        FastPreviewManager.getInstance(project).enable()
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
    }
  }
}