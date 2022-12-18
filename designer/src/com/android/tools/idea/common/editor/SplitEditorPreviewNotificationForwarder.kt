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
package com.android.tools.idea.common.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/**
 * Interface to implement by [SplitEditor] previews that wish to handle their own notifications.
 */
interface SplitEditorPreviewNotificationHandler {
  /**
   * Called when the notifications need to be updated.
   */
  fun updateNotifications()
}

/**
 * [EditorNotifications.Provider] that simply listens for notification update calls and forwards it to the [SplitEditorPreviewNotificationHandler].
 * This allows the [SplitEditor] preview panels to **optionally** its own notification system that displays notifications that only cover
 * the preview side and not the whole editor.
 *
 * This also allows for split editor preview notifications to use exactly the same interface as [EditorNotifications] so they can
 * easily be refactored.
 */
class SplitEditorPreviewNotificationForwarder : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
    return Function { createNotificationPanel(it) }
  }
  private fun createNotificationPanel(fileEditor: FileEditor): EditorNotificationPanel? {
    // If the given FileEditor is DesignerEditor, forward the update to the MultiRepresentationPreview so it can
    // pass the notification handling to the corresponding representations.
    ((fileEditor as? SplitEditor<*>)?.preview as? SplitEditorPreviewNotificationHandler)?.updateNotifications()

    // We never create EditorNotificationPanel so return null. The DesignFilesPreviewEditor will handle the notifications.
    return null
  }
}