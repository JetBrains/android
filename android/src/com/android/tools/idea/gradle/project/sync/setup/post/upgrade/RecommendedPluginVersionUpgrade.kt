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
@file:JvmName("RecommendedPluginVersionUpgrade")
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade

import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgrade
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project

val AGP_UPGRADE_NOTIFICATION_GROUP = NotificationGroup("Android Gradle Upgrade Notification", NotificationDisplayType.STICKY_BALLOON, true)

fun checkAndShowNotification(project: Project) {
  val upgrade = PluginVersionUpgrade.getInstance(project)
  if (upgrade.isRecommendedUpgradable) {
    val existing = NotificationsManager
      .getNotificationsManager()
      .getNotificationsOfType<ProjectUpgradeNotification>(ProjectUpgradeNotification::class.java, project)

    if (existing.isEmpty()) {
      val listener = NotificationListener { notification, _ ->
        notification.expire()
        upgrade.performRecommendedUpgrade()
      }

      val notification = ProjectUpgradeNotification(listener)
      notification.notify(project)
    }
  }
}

@VisibleForTesting
class ProjectUpgradeNotification(listener: NotificationListener)
  : Notification(AGP_UPGRADE_NOTIFICATION_GROUP.displayId,
                 "Plugin Update Recommended",
                 IdeBundle.message("updates.ready.message", "Android Gradle Plugin"),
                 NotificationType.INFORMATION,
                 listener)
