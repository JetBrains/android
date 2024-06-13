/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.settings.LayoutInspectorConfigurable
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle

class ShowLayoutInspectorAction :
  DumbAwareAction(
    AndroidBundle.message("android.ddms.actions.layoutinspector.title"),
    AndroidBundle.message("android.ddms.actions.layoutinspector.description"),
    StudioIcons.Shell.Menu.LAYOUT_INSPECTOR
  ) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled =
      project != null && CommonAndroidUtil.getInstance().isAndroidProject(project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled) {
      showLayoutInspectorDiscoveryPopUp(project)
      activateToolWindow(project, RUNNING_DEVICES_TOOL_WINDOW_ID)
    } else {
      activateToolWindow(project, LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
    }
  }

  private fun showLayoutInspectorDiscoveryPopUp(project: Project) {
    val notificationGroup =
      NotificationGroupManager.getInstance().getNotificationGroup("LAYOUT_INSPECTOR_DISCOVERY")
    val notification =
      notificationGroup.createNotification(
        LayoutInspectorBundle.message("layout.inspector.discovery.title"),
        LayoutInspectorBundle.message("layout.inspector.discovery.description"),
        NotificationType.INFORMATION,
      )
    notification.addAction(
      object : NotificationAction(LayoutInspectorBundle.message("opt.out")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, LayoutInspectorConfigurable::class.java)
        }
      }
    )
    notification.notify(project)
  }

  private fun activateToolWindow(project: Project, toolWindowId: String) {
    ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.activate(null)
  }
}
