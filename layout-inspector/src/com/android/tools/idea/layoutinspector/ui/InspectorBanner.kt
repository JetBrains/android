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

import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A banner for showing notifications in the Layout Inspector.
 */
class InspectorBanner(project: Project) : JPanel() {
  private val bannerService = InspectorBannerService.getInstance(project)
  private var notifications: List<StatusNotification> = emptyList()

  init {
    isVisible = false
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    bannerService?.notificationListeners?.add(::updateNotifications)
  }

  private fun updateNotifications() {
    // fire the data changed on the UI thread:
    ApplicationManager.getApplication().invokeLater {
      removeAll()
      notifications = bannerService?.notifications ?: emptyList()
      isVisible = notifications.isNotEmpty()
      notifications.forEach { notification ->
        val panel = EditorNotificationPanel(notification.status)
        panel.text = notification.message
        notification.actions.forEach { action ->
          panel.createActionLabel(action.templatePresentation.description ?: action.templateText ?: "") {
            val context = DataContext { dataId -> if (NOTIFICATION_KEY.`is`(dataId)) notification else null }
            val presentation = action.templatePresentation.clone()
            val event = AnActionEvent(null, context, ActionPlaces.NOTIFICATION, presentation, ActionManager.getInstance(), 0)
            action.update(event)
            action.actionPerformed(event)
          }
        }
        add(panel)
      }
      // Update the InspectorBanner height:
      revalidate()
    }
  }
}
