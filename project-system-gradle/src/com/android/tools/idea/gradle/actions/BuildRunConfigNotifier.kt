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
package com.android.tools.idea.gradle.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

object BuildRunConfigNotifier {
  fun notifyNoRunConfigFound(project: Project) {
    val logMessage =
      String.format("Unable to find Run Configuration to build: No Configuration selected.")
    logger.warn(logMessage)


    val message =
      String.format("Unable to find Run Configuration to build: No Configuration selected.")
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Android Gradle Tasks")
      .createNotification(message, NotificationType.WARNING)
      .notify(project)
  }

  fun notifyNoModulesFoundToBuild(runConfigName: String, project: Project) {
    val logMessage =
      String.format("Unable to find modules to build for '%s' Run Configuration", runConfigName)
    logger.warn(logMessage)


    val message =
      String.format("Unable to find modules to build for '%s' Run Configuration", runConfigName)
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Android Gradle Tasks")
      .createNotification(message, NotificationType.WARNING)
      .notify(project)
  }

  private val logger: Logger
    get() = Logger.getInstance(BuildRunConfigNotifier::class.java)
}
