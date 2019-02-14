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
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

val NOTIFICATION_GROUP = NotificationGroup("Android Gradle Upgrade Notification", NotificationDisplayType.STICKY_BALLOON, true)

class PluginVersionUpgradeChecker(private val reminder: TimeBasedUpgradeReminder = TimeBasedUpgradeReminder()) : StartupActivity {

  override fun runActivity(project: Project) {
    checkUpgrade(project, reminder)
  }

  companion object {

    /**
     * Show or schedule the balloon notification when project is upgradable.
     */
    @JvmStatic
    @JvmOverloads
    fun checkUpgrade(project: Project, reminder: TimeBasedUpgradeReminder = TimeBasedUpgradeReminder()) {
      val nextReminderTime = TimeBasedUpgradeReminder().getStoredTimestamp(project)?.toLongOrNull() ?: 0
      val current = System.currentTimeMillis()

      if (current >= nextReminderTime) {
        // It is 1 day longer after last checking, or user didn't ask to remind later if nextReminderTime is 0.
        checkAndShowNotification(project, reminder)
      }
      else {
        // It is less than 1 day after last checking, schedule next checking.
        scheduleNextReminder(project, nextReminderTime - current, TimeUnit.MILLISECONDS, reminder)
      }
    }

    @JvmStatic
    @JvmOverloads
    fun scheduleNextReminder(project: Project,
                             delay: Long,
                             unit: TimeUnit,
                             reminder: TimeBasedUpgradeReminder = TimeBasedUpgradeReminder()) {
      val executors = Executors.newScheduledThreadPool(1)
        .apply { schedule({ checkAndShowNotification(project, reminder) }, delay, unit) }
      Disposer.register(project, Disposable { executors.shutdownNow() })
    }
  }
}

private fun checkAndShowNotification(project: Project, reminder: TimeBasedUpgradeReminder) {
  val upgrade = PluginVersionUpgrade.getInstance(project)
  if (upgrade.isUpgradable && reminder.shouldAskForUpgrade(project)) {
    val existing = NotificationsManager
      .getNotificationsManager()
      .getNotificationsOfType<ProjectUpgradeNotification>(ProjectUpgradeNotification::class.java, project)

    if (existing.isEmpty()) {
      val listener = NotificationListener { notification, _ ->
        notification.expire()
        upgrade.checkAndPerformUpgrade()
      }

      val notification = ProjectUpgradeNotification(listener)
      notification.notify(project)
    }
  }
}

@VisibleForTesting
class ProjectUpgradeNotification(listener: NotificationListener)
  : Notification(NOTIFICATION_GROUP.displayId,
                 "Plugin Update Recommended",
                 IdeBundle.message("updates.ready.message", "Android Gradle Plugin"),
                 NotificationType.INFORMATION,
                 listener)
