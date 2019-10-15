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
package com.android.tools.idea.compose.preview

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.project.Project


// Handling of old preview package notification. A warning will be displayed for users importing the old Preview package name
// that was used during testing. Users should migrate to the new androidx package. This is only a problem for internal users
// since we never deployed the old Preview package to users.

private val OLD_PACKAGE_NOTIFICATION_GROUP =
  NotificationGroup("Old package used", NotificationDisplayType.STICKY_BALLOON, false)

private val notificationManager by lazy {
  SingletonNotificationManager(OLD_PACKAGE_NOTIFICATION_GROUP, NotificationType.WARNING, null)
}

fun defaultOldPackageNotificationsHandler(project: Project) {
  notificationManager.notify(message("notification.old.package", PREVIEW_ANNOTATION_FQN), project)
}