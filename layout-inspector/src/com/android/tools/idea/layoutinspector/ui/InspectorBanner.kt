/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A banner for showing notifications in the Layout Inspector.
 */
class InspectorBanner(private val notificationModel: NotificationModel) : JPanel() {
  private var notifications: List<StatusNotification> = emptyList()

  init {
    isVisible = false
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    notificationModel.notificationListeners.add(::updateNotifications)
  }

  private fun updateNotifications() {
    // fire the data changed on the UI thread:
    ApplicationManager.getApplication().invokeLater {
      removeAll()
      notifications = notificationModel.notifications
      isVisible = notifications.isNotEmpty()
      notifications.forEach { notification ->
        val panel = EditorNotificationPanel(notification.status)
        panel.text = notification.message
        notification.actions.forEach { action ->
          panel.createActionLabel(action.name) { action.invoke(notification) }
        }
        add(panel)
      }
      // Update the InspectorBanner height:
      revalidate()
    }
  }
}
