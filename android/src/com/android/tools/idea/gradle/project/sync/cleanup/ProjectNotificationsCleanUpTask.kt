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
package com.android.tools.idea.gradle.project.sync.cleanup

import com.android.tools.idea.IdeInfo
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.project.Project



/** A copy of a private constant in AbstractModuleDataService. ProjectNotificationsCleanUpTaskTest verifies it doesn't get changed. */
const val BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME = "Build sync orphan modules"

/**
 * A clean up task that resets the configuration of "Build sync orphan modules" notification group to "not display" and "not log"
 * in order to prevent a notification which allows users to restore the removed module as a non-Gradle module. Non-Gradle modules
 * are not supported by AS in Gradle projects.
 */
class ProjectNotificationsCleanUpTask : ProjectCleanUpTask() {
  override fun cleanUp(project: Project) {
    if (IdeInfo.getInstance().isAndroidStudio) {
      NotificationsConfiguration
          .getNotificationsConfiguration()
          .changeSettings(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false)
    }
  }
}
