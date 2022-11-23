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
package com.android.tools.idea.execution.common

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Provides any information to user in balloon while user starts configuration(deploy/launch/connect debugger).
 */
object RunConfigurationNotifier {
  fun notifyWarning(
    project: Project,
    configurationName: String,
    text: String
  ) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Run Configuration")
      .createNotification(title = "Run $configurationName", text, NotificationType.WARNING)
      .notify(project)
  }

  fun notifyInfo(
    project: Project,
    configurationName: String,
    text: String
  ) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Run Configuration")
      .createNotification(title = "Run $configurationName", text, NotificationType.INFORMATION)
      .notify(project)
  }

  fun notifyError(
    project: Project,
    configurationName: String,
    text: String
  ) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Run Configuration")
      .createNotification(title = "Run $configurationName", text, NotificationType.ERROR)
      .notify(project)
  }

  fun notifyErrorWithAction(
    project: Project,
    configurationName: String,
    text: String,
    action: NotificationAction
  ) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Run Configuration")
      .createNotification(title = "Run $configurationName", text, NotificationType.ERROR)
      .addAction(action)
      .notify(project)
  }
}