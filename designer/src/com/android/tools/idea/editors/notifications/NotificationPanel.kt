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
package com.android.tools.idea.editors.notifications

import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import javax.swing.Box
import javax.swing.JPanel
import kotlin.streams.asSequence

/**
 * A panel that displays notifications or hides itself if there are none. It is intended to be used together with a [FileEditor] and
 * utilizes the mechanism of [EditorNotifications.Provider]s extending [epName] extension point to get [EditorNotificationPanel]s to
 * display.
 */
class NotificationPanel(
  private val NOTIFICATIONS_EP_NAME: ExtensionPointName<EditorNotifications.Provider<EditorNotificationPanel>>
) : JPanel(VerticalLayout(0)) {

  private val notificationsPanel: Box = Box.createVerticalBox().apply {
    name = "NotificationsPanel"
  }

  // The notificationsWrapper helps pushing the notifications to the top of the layout. This whole panel will be hidden if no notifications
  // are available.
  init {
    add(notificationsPanel, VerticalLayout.FILL_HORIZONTAL)
  }

  fun updateNotifications(virtualFile: VirtualFile, parentEditor: FileEditor, project: Project) {
    notificationsPanel.removeAll()
    NOTIFICATIONS_EP_NAME.extensionList
      .asSequence()
      .mapNotNull { it.createNotificationPanel(virtualFile, parentEditor, project) }
      .forEach {
        notificationsPanel.add(it)
      }

    // If no notification panels were added, we will hide the notifications panel
    if (notificationsPanel.componentCount > 0) {
      isVisible = true
      notificationsPanel.revalidate()
    }
    else {
      isVisible = false
    }
  }
}