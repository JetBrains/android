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

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

/**
 * [EditorNotifications.Provider] that simply listens for notification update calls and forwards it to the right Preview editor.
 * This allows the [PreviewEditor] to have its own notification system that displays notifications that only cover the preview side
 * and not the whole editor.
 *
 * This also allows for Compose Preview notifications to use exactly the same interface as [EditorNotifications] so they can easily
 * be refactored.
 */
class ComposePreviewEditorNotificationAdapter : EditorNotifications.Provider<EditorNotificationPanel>() {

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    // If the given FileEditor is part of the Compose Preview, forward the update to the PreviewEditor so it can handle its own
    // notifications.
    (fileEditor as? ComposeTextEditorWithPreview)?.preview?.updateNotifications()

    // We never create a full editor notification so return null. The PreviewEditor will handle its own notifications.
    return null
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY

  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.notification.forwarder")
}