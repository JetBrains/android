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
package com.android.tools.idea.customview.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors

internal class CustomViewPreviewNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.notification")

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    val previewManager = fileEditor.getCustomViewPreviewManager() ?: return null
    return when (previewManager.notificationsState) {
      CustomViewPreviewManager.NotificationsState.CODE_MODIFIED -> EditorNotificationPanel().apply {
        setText("Source code is modified. A rebuild is required to show updated previews.")
      }
      CustomViewPreviewManager.NotificationsState.BUILDING -> EditorNotificationPanel().apply {
        setText("Building...")
        icon(AnimatedIcon.Default())
      }
      CustomViewPreviewManager.NotificationsState.BUILD_FAILED -> EditorNotificationPanel(LightColors.RED).apply {
        setText("Correct preview cannot be displayed until after a successful build.")
      }
      else -> null
    }
  }

  override fun getKey() = COMPONENT_KEY
}