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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.gradle.buildNamePrefixedGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.getBuildAndRelativeGradleProjectPath
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module

object GradleTaskFinderNotifier {
  fun notifyNoTaskFound(modules: Array<Module>, mode: BuildMode, type: TestCompileType) {
    if (modules.isEmpty()) return
    val modulePaths = modules
      .mapNotNull { it.getBuildAndRelativeGradleProjectPath()?.buildNamePrefixedGradleProjectPath() }
      .distinct()

    val project = modules[0].project
    val logModuleNames = modulePaths.take(MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE)
      .joinToString(", ") + if (modulePaths.size > MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE) "..." else ""

    val logMessage =
      String.format("Unable to find Gradle tasks to build: [%s]. Build mode: %s. Tests: %s.", logModuleNames, mode, type.displayName)
    logger.warn(logMessage)
    val moduleNames = modulePaths.take(MAX_MODULES_TO_SHOW_IN_NOTIFICATION)
      .joinToString(", ") + if (modulePaths.size > 5) "..." else ""

    val message =
      String.format("Unable to find Gradle tasks to build: [%s]. <br>Build mode: %s. <br>Tests: %s.", moduleNames, mode, type.displayName)
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Android Gradle Tasks")
      .createNotification(message, NotificationType.WARNING)
      .notify(project)
  }

  private val logger: Logger
    get() = Logger.getInstance(GradleTaskFinder::class.java)
}

private const val MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE = 50
private const val MAX_MODULES_TO_SHOW_IN_NOTIFICATION = 5
