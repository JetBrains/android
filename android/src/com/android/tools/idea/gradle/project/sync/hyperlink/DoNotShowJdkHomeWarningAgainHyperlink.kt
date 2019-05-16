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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.project.Project

/**
 * A [NotificationHyperlink] that stops showing a warning when the JDK used by Studio is not the same as Java Home.
 */
class DoNotShowJdkHomeWarningAgainHyperlink : NotificationHyperlink(
  "doNotShowJdkHomeWarning", "Do not show this warning again") {

  public override fun execute(project: Project) {
    val msg = "Warnings about JDK location not being JAVA_HOME were disabled.\n" +
              "They can be enabled again in the Settings dialog."
    JDK_LOCATION_WARNING_NOTIFICATION_GROUP.createNotification(msg, NotificationType.INFORMATION).notify(project)
    NotificationsConfiguration.getNotificationsConfiguration().changeSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId,
                                                                              JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayType,
                                                                              false /* do not log */, false /* not read aloud */)
  }
}
