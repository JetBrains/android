/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.notifications

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.NlEditor
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAccessoryPanel
import com.android.tools.idea.uibuilder.surface.AccessoryPanel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

private class DeprecationNotification() : EditorNotificationPanel(Status.Warning) {
  init {
    text = "Motion Editor will be deprecated in the next release, please update your usage accordingly."
    createActionLabel("Learn More", Runnable {
      // TODO(356454306)
    })
  }
}

class MotionEditorNotificationProvider : EditorNotificationProvider {
  private fun createNotificationPanel(fileEditor: FileEditor): EditorNotificationPanel? {
    if (!StudioFlags.MOTION_EDITOR_DEPRECATION_WARNING.get()) return null
    val nlEditor = fileEditor as? NlEditor ?: return null
    val designSurface = nlEditor.component.surface
    val accessoryPanel = (designSurface.accessoryPanel as? AccessoryPanel)?.currentPanel ?: return null
    return if (accessoryPanel is MotionAccessoryPanel) DeprecationNotification() else null
  }

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> = Function { fileEditor ->
    createNotificationPanel(fileEditor)
  }

}